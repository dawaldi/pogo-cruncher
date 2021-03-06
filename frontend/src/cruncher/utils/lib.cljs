(ns cruncher.utils.lib
  (:require [om.next :as om :refer-macros [defui]]
            [goog.dom :as gdom]
            [cruncher.data.pokemon :as pokemon]))

(defonce app-state
         (atom
           {:pokemon           []
            :user              {:view       :default
                                :logged-in? false}
            :error             {:message nil}
            :info              {:message nil}
            :app               {:loading? false}
            :progress          {:running   false
                                :to_delete 100
                                :deleted   0
                                :status    "ok"}
            :progress-running? false}))

(declare get-pokemon-by-id)

;;;; React compatibility
(defonce counter (atom 0))

(defn get-unique-key
  "Return unique react-key."
  []
  (str "cruncher-unique-react-key-" (swap! counter inc)))

(defn merge-react-key
  "Get a unique key, create a small map with :react-key property and merge it with the given collection."
  [col]
  (merge {:react-key (get-unique-key)} col))


;;;; Reconciler action
(defmulti read (fn [env key params] key))

(defmethod read :default
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ value] (find st key)]
      {:value value}
      {:value :not-found})))

(defmulti mutate om/dispatch)

(defmethod mutate 'update/pokemon
  [{:keys [state]} _ {:keys [pokemon]}]
  (let [named-pokemon (map (fn [pokemap] (merge pokemap {:name (:name (get-pokemon-by-id (:pokemon_id pokemap)))})) pokemon)]
    {:action (fn [] (swap! state update-in [:pokemon] (fn [] named-pokemon)))}))

(defmethod mutate 'sort/pokemon
  [{:keys [state]} _ {:keys [key]}]
  {:action (fn [] (swap! state update-in [:pokemon] (fn []
    (reverse (sort-by (juxt key :individual_percentage :cp) (:pokemon @state))))))})

(defmethod mutate 'change/view
  [{:keys [state]} _ {:keys [view]}]
  {:action (fn [] (swap! state update-in [:user :view] (fn [] view)))})

(defmethod mutate 'app/loading
  [{:keys [state]} _ {:keys [status]}]
  {:action (fn [] (swap! state update-in [:app :loading?] (fn [] status)))})

(defmethod mutate 'update/error
  [{:keys [state]} _ {:keys [message]}]
  {:action (fn [] (swap! state update-in [:error :message] (fn [] message)))})

(defmethod mutate 'update/info
  [{:keys [state]} _ {:keys [message]}]
  {:action (fn [] (swap! state update-in [:info :message] (fn [] message)))})

(defmethod mutate 'user/logged-in
  [{:keys [state]} _ {:keys [status]}]
  {:action (fn [] (swap! state update-in [:user :logged-in?] (fn [] status)))})

(defmethod mutate 'user/remove-pokemon-cache
  [{:keys [state]} _]
  {:action (fn [] (swap! state update-in [:pokemon] (fn [] [])))})

(defmethod mutate 'status/progress
  [{:keys [state]} _ {:keys [status]}]
  {:action (fn [] (swap! state update-in [:progress]
                         (fn [] {:status    (:status status)
                                 :to_delete (:to_delete status)
                                 :deleted   (:deleted status)})))})

(defmethod mutate 'toggle/progress
  [{:keys [state]} _ {:keys [status]}]
  {:action (fn [] (swap! state update-in [:progress-running?] (fn [] status)))})

(defonce reconciler
         (om/reconciler
           {:state  app-state
            :parser (om/parser {:read read :mutate mutate})}))


;;;; Get stuff
(defn inventory-pokemon
  "Return all pokemon which are currently stored."
  []
  (get-in @app-state [:pokemon]))

(defn get-pokemon-by-id
  "Look up database to return complete pokemon by its id."
  [pokemon-id]
  (get pokemon/all pokemon-id))

(defn logged-in?
  "Return boolean if user is logged in or not."
  []
  (get-in @app-state [:user :logged-in?]))

(defn logged-in!
  "Set boolean if user is logged in or not. If no parameters are given, set logged-in to true."
  ([bool]
   (if (not bool) (om/transact! reconciler `[(user/remove-pokemon-cache {})]))
   (om/transact! reconciler `[(user/logged-in {:status ~bool})]))
  ([] (logged-in! true)))


;;;; About views
(defn current-view
  "Return current selected view."
  []
  (get-in @app-state [:user :view]))

(defn change-view!
  "Return current selected view."
  [key]
  (om/transact! reconciler `[(change/view {:view ~key})]))

(defn loading?
  "Return boolean if ajax request is still in process."
  []
  (get-in @app-state [:app :loading?]))

(defn loading!
  "Set boolean if ajax request is still in process. Defaults to true without parameters."
  ([bool]
   (om/transact! reconciler `[(app/loading {:status ~bool})]))
  ([] (loading! true)))


;;;; Error messages
(defn error?
  "Return boolean if there is an error message."
  []
  (let [message (get-in @app-state [:error :message])]
    (pos? (count message))))

(defn error!
  "Set error message."
  [message]
  (om/transact! reconciler `[(update/error {:message ~message})]))

(defn no-error!
  "Reset error message."
  []
  (error! nil))

(defn get-error
  "Return error message."
  []
  (get-in @app-state [:error :message]))


;;;; Info box
(defn info?
  "Return boolean if there is an error message."
  []
  (let [message (get-in @app-state [:info :message])]
    (pos? (count message))))

(defn info!
  "Set error message."
  [message]
  (om/transact! reconciler `[(update/info {:message ~message})]))

(defn no-info!
  "Reset error message."
  []
  (info! nil))

(defn get-info
  "Return error message."
  []
  (get-in @app-state [:info :message]))


;;;; Status information
(defn update-progress-status!
  "Receives a map containing information about the progress status, which are then stored in the app-state."
  [response]
  (om/transact! reconciler `[(status/progress {:status ~response})]))

(defn progress!
  "Toggle progress bar."
  [bool]
  (om/transact! reconciler `[(toggle/progress {:status ~bool})]))

(defn progress?
  "Return bool if a progress is running."
  []
  (get-in @app-state [:progress-running?]))


;;;; State transitions
(defn update-pokemon!
  "Update pokemon based on API response."
  [res]
  (om/transact! reconciler `[(update/pokemon {:pokemon ~res})]))

(defn sort-pokemon!
  "Sort complete list of pokemon by given key."
  [key]
  (om/transact! reconciler `[(sort/pokemon {:key ~key})]))


;;;; Conversions
(defn str->int
  "Convert String to Integer."
  [s]
  (let [converted (js/parseInt s)]
    (if-not (js/isNaN converted)
      converted
      s)))