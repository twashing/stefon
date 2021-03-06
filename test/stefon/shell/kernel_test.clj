(ns stefon.shell.kernel-test
  (:use clojure.test
        midje.sweet)
  (:require [clojure.pprint :refer :all]
            [clojure.core.async :as async :refer :all]
            [stefon.core :as core]
            [stefon.shell :as shell]
            [stefon.shell.kernel :as kernel]
            [stefon.shell.plugin :as plugin]
            [stefon.shell.kernel-process :as kprocess]
            [heartbeat.plugin :as heartbeat]))


(defn- system-shape []
  {:domain {:posts []
            :assets []
            :tags []}})

(defn- matches-domain-shape [input-shape]
  (= input-shape (system-shape)))

(defn- matches-system-shape [input-shape]
  (= input-shape {:stefon/system (system-shape)}))

(deftest test-kernel-1

  ;; Create & Manage the System
  (testing "System generation"
    (let [kernel-result (kernel/generate-system)]
      (is (not (nil? kernel-result)))
      (is (map? kernel-result))
      (is (matches-domain-shape kernel-result))))

  (testing "Kernel Startup"
    (let [start-result (kernel/start-system)]

      (is (not (nil? @start-result)))
      (is (map? @start-result))
      (is (= (keys (system-shape)) (keys (:stefon/system @start-result))) )

      (let [system-state (kernel/get-system)]

        (is (not (nil? @system-state)))
        (is (map? @system-state))
        (is (= (keys (system-shape)) (keys (:stefon/system @system-state)))))))

  (testing "Should already have a core channel-list "

      (let [xx (kernel/start-system)]

        (is (not (nil? (-> @(kernel/get-system) :channel-list)))) ))


  (testing "Should be able to add channels to a list"

      (let [new-channel (chan)

            xx (kernel/start-system)
            system-atom (kernel/get-system)

            add-result (try
                         (plugin/add-to-channel-list system-atom new-channel)
                         (catch Exception e e))
            add-result-2 (try
                           (plugin/add-to-channel-list system-atom {:id 2 :channel new-channel})
                           (catch Exception e e))
            add-result-3 (try
                           (plugin/add-to-channel-list system-atom {:id "ID" :channel new-channel})
                           (catch Exception e e))]

        (is (not (nil? add-result)))
        (is (= RuntimeException (type add-result)))
        (is (= RuntimeException (type add-result-2)))

        (is (not (empty? (-> add-result-3 :channel-list))))

        (is (not (empty? (-> @(kernel/get-system) :channel-list))))
        (is (map? (first (-> @(kernel/get-system) :channel-list))))))

  (testing "on kernel bootstrap, SHOULD have kernel channel"

      (let [system-atom (kernel/start-system)
            result (plugin/get-kernel-channel system-atom)]

        (is (not (nil? result)))
        (is (= "kernel-channel" (:id result)))))


  (testing "on kernel bootstrap, SHOULD have 1 kernel-receive function"

      (let [xx (kernel/start-system) ]

        (is (not (empty? (-> @(kernel/get-system) :receive-fns))))
        (is (fn? (-> @(kernel/get-system) :receive-fns first :fn)))

        ;; sending on the kernel channel should spark the kernel retrieve
        ;; ...
        ))

  (testing "on attaching a plugin, plugin SHOULD have 1 new send fn on kernel-channel"

    (let [xx (kernel/start-system)

          handlerfn (fn [system-atom msg] )
          result (shell/attach-plugin handlerfn)]

      ((:sendfn result) {:id "asdf" :message {:fu :bar}})
      (is (fn? (:sendfn result)))

      ;; using the send fn should spark the kernel retrieve
      ;; ...
      ))

  (testing "on attaching a plugin, plugin SHOULD have 1 new receive fn on the new-channel"

      (let [xx (kernel/start-system)

            handlerfn (fn [system-atom msg] )
            result (shell/attach-plugin handlerfn)]

        (is (fn? (:receivefn result)))

        ;; sending on new channel, should spark plugin's rettrieve
        ;; ...
        ))


  (testing "on attaching a plugin, kernel SHOULD have 1 new send fn on the new-channel"

      (let [xx (kernel/start-system)

            handlerfn (fn [system-atom msg] )
            result (shell/attach-plugin handlerfn)]

        (is (not (empty? (-> @(kernel/get-system) :send-fns))))
        (is (fn? (:fn (first (-> @(kernel/get-system) :send-fns)))))

        ;; using new send fn, should spark plugin's retrieve
        ;; ...
        ))


  ;; PLUGIN
  (testing "Should be able to send-message-raw to attached functions"

    (let [xx (kernel/stop-system)
          xx (kernel/start-system)
          system-atom (kernel/get-system)
          p1 (promise)
          p2 (promise)
          p3 (promise)

          h1 (fn [system-atom msg] (deliver p1 msg))
          h2 (fn [system-atom msg] (deliver p2 msg))
          h3 (fn [system-atom msg] (deliver p3 msg))

          r1 (shell/attach-plugin h1)
          r2 (shell/attach-plugin h2)
          r3 (shell/attach-plugin h3)]

      (kprocess/send-message-raw system-atom
                                 [(:id r2) (:id r3)]
                                 {:id "qwerty-1234" :fu :bar})

      (is (not (= "qwerty-1234" (:id p1))))
      (is (not (nil? @p2)))
      (is (not (nil? @p3)))

      (is (= {:id "qwerty-1234" :fu :bar} @p2))
      (is (= {:id "qwerty-1234" :fu :bar} @p3))))


  (testing "Should be able to send-message to attached functions :include"

    (let [xx (kernel/stop-system)
          xx (kernel/start-system)
          system-atom (kernel/get-system)

          p1 (promise)
          p2 (promise)
          p3 (promise)

          h1 (fn [system-atom msg] (deliver p1 msg))
          h2 (fn [system-atom msg] (deliver p2 msg))
          h3 (fn [system-atom msg] (deliver p3 msg))

          r1 (shell/attach-plugin h1)
          r2 (shell/attach-plugin h2)
          r3 (shell/attach-plugin h3)]

      (kprocess/send-message system-atom
                             {:include [(:id r2) (:id r3)]}
                             {:id "qwerty-1234" :fu :bar})

      (is (not (realized? p1)))
      (is (not (nil? @p2)))
      (is (not (nil? @p3)))

      (is (= {:id "qwerty-1234" :fu :bar} @p2))
      (is (= {:id "qwerty-1234" :fu :bar} @p3))))


  (testing "Should be able to send-message to attached functions :exclude"

      (let [xx (kernel/stop-system)
            xx (kernel/start-system)
            system-atom (kernel/get-system)

            p1 (promise)
            p2 (promise)
            p3 (promise)

            h1 (fn [system-atom msg]

                 (deliver p1 msg)

                 (is (realized? p1))
                 (is (not (realized? p2)))
                 (is (not (realized? p3))))
            h2 (fn [system-atom msg] (deliver p2 msg))
            h3 (fn [system-atom msg] (deliver p3 msg))

            r1 (shell/attach-plugin h1)
            r2 (shell/attach-plugin h2)
            r3 (shell/attach-plugin h3)]

        (kprocess/send-message system-atom
                               {:exclude [(:id r2) (:id r3)]}
                               {:id "qwerty-1234" :message {:fu :bar}})))


  ;; include TEE infrastructure
  ;; ...

  (testing "Should send a message that the kernel DOES understand, then forwards (check for recursive message)"

    (let [xx (kernel/stop-system)
          xx (kernel/start-system)
          system-atom (kernel/get-system)

          ptee (promise)
          teefn (fn [system-atom msg]

                  (deliver ptee msg)

                  (is (realized? ptee))
                  (is (= '(:id :message) (keys @ptee))))
          xx (plugin/add-receive-tee system-atom teefn)

          handlerfn (fn [system-atom msg])
          result (shell/attach-plugin handlerfn)

          date-one (-> (java.text.SimpleDateFormat. "MM/DD/yyyy") (.parse "09/01/2013"))]

      ((:sendfn result) {:id (:id result) :message {:stefon.post.create {:parameters {:title "Latest In Biotech"
                                                                                      :content "Lorem ipsum."
                                                                                      :content-type "txt"
                                                                                      :created-date date-one
                                                                                      :modified-date date-one
                                                                                      :assets []
                                                                                      :tags []}} }})
      ;; check for recursive message
      ;; ...

      ))


  (testing "Should send a message that the kernel DOES NOT understand, just forwards (check for recursive message)"

    (let [xx (kernel/stop-system)
          xx (kernel/start-system)

          p1 (promise)
          p2 (promise)
          p3 (promise)

          h1 (fn [system-atom msg] (deliver p1 msg))
          h2 (fn [system-atom msg] (deliver p2 msg))
          h3 (fn [system-atom msg] (deliver p3 msg))

          r1 (shell/attach-plugin h1)
          r2 (shell/attach-plugin h2)
          r3 (shell/attach-plugin h3)

          message {:id (:id r1) :message {:fu :bar}}]

      ((:sendfn r1) message)

      @p2 ;; cheating
      @p3

      (is (not (realized? p1)))
      (is (realized? p2))
      (is (realized? p3))

      ;; message should be unmodified
      (is (= message @p2))))


  (testing "Should send a message from plugin to kernel, and get a return value (check for recursive message)"

        (let [xx (kernel/stop-system)
              xx (kernel/start-system)

              p1 (promise)
              handlerfn (fn [system-atom msg]
                          (deliver p1 msg))
              result (shell/attach-plugin handlerfn)

              date-one (-> (java.text.SimpleDateFormat. "MM/DD/yyyy") (.parse "09/01/2013"))]

          ((:sendfn result) {:id (:id result) :message {:stefon.post.create {:parameters {:title "Latest In Biotech"
                                                                                          :content "Lorem ipsum."
                                                                                          :content-type "txt"
                                                                                          :created-date date-one
                                                                                          :modified-date date-one
                                                                                          :assets []
                                                                                          :tags []}} }})

          ;; check for recursive message
          ;; ...

          @p1 ;; cheating

          (is (realized? p1))
          (is (not (nil? (:result @p1))))
          (is (= stefon.domain.Post (type (:result @p1))))))


  (testing "Should send a message from plugin1 -> kernel -> plugin2; then cascade return value from plugin2 -> kernel -> plugin1"

      (let [xx (kernel/start-system)

            ;; gymnastics SETUP
            h3-send (promise)
            r3 {}

            ;; PROMISEs
            p1 (promise)
            p2 (promise)
            p3 (promise)

            ;; HANDLERs
            h1 (fn [system-atom msg]

                 ;;(println ">> h1 CALLED > " msg)
                 (deliver p1 msg))

            h2 (fn [system-atom msg] (deliver p2 msg))
            h3 (fn [system-atom msg]

                 ;; make h3 handle 'plugin.post.create'
                 (let [ppcreate (-> msg :plugin.post.create :message)]

                   #_(println ">> h3 > " @h3-send)
                   (@h3-send msg)))

            ;; ATTACH results
            r1 (shell/attach-plugin h1)
            r2 (shell/attach-plugin h2)
            r3 (shell/attach-plugin h3)

            ;; h3-send SETUP
            xx (deliver h3-send (fn [msg]

                                  #_(println ">> h3-send [" {:id (:id r3)
                                                           :origin (-> msg :plugin.post.create :id)
                                                           :action :noop
                                                           :result {:fu :bar}} "]")

                                  #_(println ">> h3-send > r3 > " r3)

                                  ((:sendfn r3) {:id (:id r3)
                                                 :origin (-> msg :plugin.post.create :id)
                                                 :action :noop
                                                 :result {:fu :bar}})))

            date-one (-> (java.text.SimpleDateFormat. "MM/DD/yyyy") (.parse "09/01/2013"))
            message {:id (:id r1) :message {:stefon.post.create {:parameters {:title "Latest In Biotech"
                                                                              :content "Lorem ipsum."
                                                                              :content-type "txt"
                                                                              :created-date date-one
                                                                              :modified-date date-one
                                                                              :assets []
                                                                              :tags []}} }}]

        ((:sendfn r1) message)
        #_(println ">> TEST Result > " @p1)

        ;; p1 will be called twice ...

        ))

  (testing "Should be able to get a channel, after we attach a plugin"

    (let [xx (kernel/stop-system)
          xx (kernel/start-system)

          p1 (promise)
          h1 (fn [system-atom msg]

               (deliver p1 msg)

               )
          r1 (shell/attach-plugin h1)
          rid (:id r1)

          message {:id rid :message {:stefon.domain.channel {:parameters {:ID rid}}}}]

      (def one ((:sendfn r1) message))

      @p1 ;; cheating by blocking until realised

      (is (realized? p1))
      (is (map? (:result @p1)))
      (is (= rid (-> @p1 :result :id)))
      (is (= clojure.core.async.impl.channels.ManyToManyChannel
                 (type (-> @p1 :result :channel)))) )))

(deftest test-kernel-2

  ;; Commuincating with Plugins
  (testing "We can GET the Plugin's plugin function"
    (let [plugin-fn (kernel/get-plugin-fn 'heartbeat.plugin)]
      (is (fn? @plugin-fn))))

  (testing "We can INVOKE the Plugin's plugin function"
    (let [plugin-receive (kernel/invoke-plugin-fn 'heartbeat.plugin)]
      (is (fn? plugin-receive))))

  (testing "We can attach the plugin to our System"
    (let [xx (kernel/stop-system)
          xx (kernel/start-system)
          plugin-result (kernel/attach-plugin kprocess/kernel-handler)]

      (is (map? plugin-result))
      (is (= '(:receivefn :sendfn :id :channel) (keys plugin-result)))))

  (testing "We can invoke plugin's (plugin) function"

    (let [plugin-result (kernel/attach-plugin-from-ns 'heartbeat.plugin)]
      (is (map? plugin-result))
      (is (= '(:receivefn :sendfn :id :channel) (keys plugin-result)))))

  (testing "Handshake 2: We can GET the Plugin's ack function"
    (let [ack-fn (kernel/get-ack-fn 'heartbeat.plugin)]
      (is (fn? @ack-fn))))

  (testing "Handshake 2: Return channel and send & receive functions"

    (let [plugin-result (kernel/attach-plugin-from-ns 'heartbeat.plugin)
          plugin-ackack (kernel/attach-plugin-ack 'heartbeat.plugin plugin-result)]
      (is (= :ack plugin-ackack))))

  (testing "The Handshake process end-to-end"

    (let [result (kernel/load-plugin 'heartbeat.plugin)]
      (is (= :ack result)))))
