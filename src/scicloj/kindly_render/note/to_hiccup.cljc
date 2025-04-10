(ns scicloj.kindly-render.note.to-hiccup
  (:require [clojure.pprint :as pprint]
            [clojure.string :as str]
            [scicloj.kindly-render.shared.walk :as walk]
            [scicloj.kindly-render.shared.util :as util]
            [scicloj.kindly-render.shared.from-markdown :as from-markdown]))

(defmulti render-advice :kind)

(defn render [note]
  (walk/advise-render-style note render-advice))

(defmethod render-advice :default [{:as note :keys [value kind]}]
  (->> (if kind
         [:div
          [:div "Unimplemented: " [:code (pr-str kind)]]
          [:code (pr-str value)]]
         (str value))
       (assoc note :hiccup)))

(defn block [class x]
  ;; TODO: can the class go on pre instead? for more visibility in the dom
  [:pre [:code {:class class} x]])

(defn code-block [x]
  (block "sourceCode language-clojure source-clojure bg-light" x))

(defn blockquote [xs]
  (into [:blockquote] xs))

(defn result-block [x]
  (blockquote [(block "sourceCode language-clojure printed-clojure" x)]))

(defn pprint-block [value]
  (result-block (binding [*print-meta* true]
                  (with-out-str (pprint/pprint value)))))

(defn message [s channel]
  (blockquote [[:strong channel] (block nil s)]))

(defmethod render-advice :kind/code [{:as note :keys [code]}]
  (->> (block "sourceCode" code)
       (assoc note :hiccup)))

(defmethod render-advice :kind/hidden [note]
  note)

(defmethod render-advice :kind/md [{:as note :keys [value]}]
  (->> (from-markdown/to-hiccup value)
       (assoc note :hiccup)))

(defmethod render-advice :kind/html [{:as note :keys [value]}]
  ;; TODO: is hiccup/raw well supported or do we need to do something?
  (->> (util/kind-str value)
       (assoc note :hiccup)))

(defmethod render-advice :kind/pprint [{:as note :keys [value]}]
  (->> (pprint-block value)
       (assoc note :hiccup)))

(defmethod render-advice :kind/image [{:as note :keys [value]}]
  (->> (if (string? value)
         [:img {:src value}]
         [:div "Image kind not implemented"])
       (assoc note :hiccup)))

;; TODO: this is problematic because it creates files
#_(defmethod render-advice :kind/image [{:keys [value]}]
    (let [image (if (sequential? value)
                  (first value)
                  value)
          png-path (files/next-file!
                     full-target-path
                     ""
                     image
                     ".png")]
      (when-not
        (util.image/write! image "png" png-path)
        (throw (ex-message "Failed to save image as PNG.")))
      [:img {:src (-> png-path
                      (str/replace
                        (re-pattern (str "^"
                                         base-target-path
                                         "/"))
                        ""))}]))


;; Data types that can be recursive

(defmethod render-advice :kind/vector [{:as note :keys [value]}]
  (walk/render-data-recursively note {:class "kind-vector"} value render))

(defmethod render-advice :kind/map [{:as note :keys [value]}]
  ;; kindly.css puts kind-map in a grid
  (walk/render-data-recursively note {:class "kind-map"} (apply concat value) render))

(defmethod render-advice :kind/set [{:as note :keys [value]}]
  (walk/render-data-recursively note {:class "kind-set"} value render))

(defmethod render-advice :kind/seq [{:as note :keys [value]}]
  (walk/render-data-recursively note {:class "kind-seq"} value render))

;; Special data type hiccup that needs careful expansion

(defmethod render-advice :kind/hiccup [note]
  (walk/render-hiccup-recursively note render))

(defmethod render-advice :kind/table [note]
  (walk/render-table-recursively note render))

(defmethod render-advice :kind/video [{:as   note
                                       :keys [youtube-id
                                              iframe-width
                                              iframe-height
                                              allowfullscreen
                                              embed-options]
                                       :or   {allowfullscreen true}}]
  (->> [:iframe
        (merge
          (when iframe-height
            {:height iframe-height})
          (when iframe-width
            {:width iframe-width})
          {:src             (str "https://www.youtube.com/embed/"
                                 youtube-id
                                 (some->> embed-options
                                          (map (fn [[k v]]
                                                 (format "%s=%s" (name k) v)))
                                          (str/join "&")
                                          (str "?")))
           :allowfullscreen allowfullscreen})]
       (assoc note :hiccup)))

#?(:clj
   (defmethod render-advice :kind/dataset [{:as note :keys [value kindly/options]}]
     (let [{:keys [dataset/print-range]} options]
       (-> value
           (cond-> print-range ((resolve 'tech.v3.dataset.print/print-range) print-range))
           (println)
           (with-out-str)
           (from-markdown/to-hiccup)
           (->> (assoc note :hiccup))))))

(defmethod render-advice :kind/tex [{:as note :keys [value]}]
  (->> (if (vector? value) value [value])
       (map (partial format "$$%s$$"))
       (str/join \newline)
       (from-markdown/to-hiccup)
       (assoc note :hiccup)))
