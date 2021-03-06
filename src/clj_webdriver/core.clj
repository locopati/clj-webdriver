;; # Clojure API for Selenium-WebDriver #
;;
;; WebDriver is a library that allows for easy manipulation of the Firefox,
;; Chrome, Safari and  Internet Explorer graphical browsers, as well as the
;; Java-based HtmlUnit headless browser.
;;
;; This library provides both a thin wrapper around WebDriver and a more
;; Clojure-friendly API for finding elements on the page and performing
;; actions on them. See the README for more details.
;;
;; Credits to mikitebeka's `webdriver-clj` project on Github for a starting-
;; point for this project and many of the low-level wrappers around the
;; WebDriver API.
;;
(ns clj-webdriver.core
  (:use [clj-webdriver driver element util window-handle options])
  (:require [clj-webdriver.js.browserbot :as browserbot-js]
            [clj-webdriver.cache :as cache]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:import [clj_webdriver.driver Driver]
           [clj_webdriver.element Element]
           [org.openqa.selenium By WebDriver WebElement Cookie
                                OutputType NoSuchElementException Keys]
           [org.openqa.selenium.firefox FirefoxDriver]
           [org.openqa.selenium.ie InternetExplorerDriver]
           [org.openqa.selenium.chrome ChromeDriver]
           [com.opera.core.systems OperaDriver]
           [org.openqa.selenium.htmlunit HtmlUnitDriver]
           [org.openqa.selenium.support.ui Select]
           [java.util Date]
           [java.io File]))

;; ## Protocols for clj-webdriver API ##

;; ### Driver/Browser Functions ###
(defprotocol IDriver
  "Basics of driver handling"
  (back [driver] "Go back to the previous page in \"browsing history\"")
  (close [driver] "Close this browser instance, switching to an active one if more than one is open")
  (current-url [driver] "Retrieve the URL of the current page")
  (forward [driver] "Go forward to the next page in \"browsing history\".")
  (get-screenshot [driver] [driver format] [driver format destination] "Take a screenshot using Selenium-WebDriver's getScreenshotAs method")
  (get-url [driver url] "Navigate the driver to a given URL")
  (page-source [driver] "Retrieve the source code of the current page")
  (quit [driver] "Destroy this browser instance")
  (refresh [driver] "Refresh the current page")
  (title [driver] "Retrieve the title of the current page as defined in the `head` tag")
  (to [driver url] "Navigate to a particular URL. Arg `url` can be either String or java.net.URL. Equivalent to the `get` function, provided here for compatibility with WebDriver API."))

;; ### Windows and Frames ###
(defprotocol ITargetLocator
  "Functions that deal with browser windows and frames"
  (window-handle [driver] "Get the only (or first) window handle, return as a WindowHandler record")
  (window-handles [driver] "Retrieve a vector of `WindowHandle` records which can be used to switchTo particular open windows")
  (other-window-handles [driver] "Retrieve window handles for all windows except the current one")
  (switch-to-frame [driver frame] "Switch focus to a particular HTML frame")
  (switch-to-window [driver handle] "Switch focus to a particular open window")
  (switch-to-other-window [driver] "Given that two and only two browser windows are open, switch to the one not currently active")
  (switch-to-default [driver] "Switch focus to the first first frame of the page, or the main document if the page contains iframes")
  (switch-to-active [driver] "Switch to element that currently has focus, or to the body if this cannot be detected"))

;; ### Finding Elements on Page ###
(defprotocol IFind
  "Functions used to locate elements on a given page"
  (find-element [driver by] "Retrieve the element object of an element described by `by`")
  (find-elements [driver by] "Retrieve a seq of element objects described by `by`")
  (find-elements-by-regex-alone [driver tag attr-val] "Given an `attr-val` pair with a regex value, find the elements that match")
  (find-elements-by-regex [driver tag attr-val])
  (find-windows [driver attr-val] "Given a browser `driver` and a map of attributes, return the WindowHandles that match")
  (find-window [driver attr-val] "Given a browser `driver` and a map of attributes, return the WindowHandles that match")
  (find-semantic-buttons [driver attr-val] "Find HTML element that is either a `<button>` or an `<input>` of type submit, reset, image or button")
  (find-semantic-buttons-by-regex [driver attr-val] "Semantic buttons are things that look or behave like buttons but do not necessarily consist of a `<button>` tag")
  (find-checkables-by-text [driver attr-val] "Finding the 'text' of a radio or checkbox is complex. Handle it here.")
  (find-table-cell [driver table coordinates] "Given a `driver`, a `table` find-it-style spec (e.g. `{:id \"my-table\"}` and a zero-based set of coordinates for row and column, return the table cell at those coordinates for the given table.")
  (find-table-row [driver table row-index] "Return all cells in the row of the given table. The `table` is a find-it-style map to identify the table uniquely (e.g., `{:id \"my-table\"}`), and `row-index` is a zero-based index of the target row.")
  (find-by-hierarchy [driver hierarchy-vector] "Given a Webdriver `driver` and a vector `hierarchy-vector`, return a lazy seq of the described elements in the hierarchy dictated by the order of elements in the `hierarchy-vector`.")
  (find-them [driver attr-val] "Find all elements that match the parameters supplied in the `attr-val` map. Also provides a shortcut to `find-by-hierarchy` if a vector is supplied instead of a map.")
  (find-it [driver attr-val] "Call (first (find-them args))"))

;; ### Acting on Elements ###
(defprotocol IElement
  "Basic actions on elements"
  (attribute [element attr] "Retrieve the value of the attribute of the given element object")
  (click [element] "Click a particular HTML element")
	(css-value [element property] "Return the value of the given CSS property")
  (displayed? [element] "Returns true if the given element object is visible/displayed")
  (drag-and-drop-by [element x y] "Drag an element by `x` pixels to the right and `y` pixels down. Use negative numbers for opposite directions.")
  (drag-and-drop-on [element-a element-b] "Drag `element-a` onto `element-b`. The (0,0) coordinates (top-left corners) of each element are aligned.")
  (exists? [element] "Returns true if the given element exists")
  (flash [element] "Flash the element in question, to verify you're looking at the correct element")
  (focus [element] "Apply focus to the given element")
  (html [element] "Retrieve the outer HTML of an element")
  (location [element] "Given an element object, return its location as a map of its x/y coordinates")
  (location-once-visible [element] "Given an element object, return its location on the screen once it is scrolled into view as a map of its x/y coordinates. The window will scroll as much as possible until the element hits the top of the page; thus even visible elements will be scrolled until they reach that point.")
  (present? [element] "Returns true if the element exists and is visible")
  (tag [element] "Retrieve the name of the HTML tag of the given element object (returned as a keyword)")
  (text [element] "Retrieve the content, or inner HTML, of a given element object")
  (value [element] "Retrieve the `value` attribute of the given element object")
  (visible? [element] "Returns true if the given element object is visible/displayed")
  (xpath [element] "Retrieve the XPath of an element"))

;; ### Acting on Form-Specific Elements ###
(defprotocol IFormElement
  "Actions for form elements"
  (clear [element] "Clear the contents of the given element object")
  (deselect [element] "Deselect a given element object")
  (enabled? [element] "Returns true if the given element object is enabled")
  (input-text [element s] "Type the string of keys into the element object")
  (submit [element] "Submit the form which contains the given element object")
  (select [element] "Select a given element object")
  (selected? [element] "Returns true if the given element object is selected")
  (send-keys [element s] "Type the string of keys into the element object")
  (toggle [element] "If the given element object is a checkbox, this will toggle its selected/unselected state. In Selenium 2, `.toggle()` was deprecated and replaced in usage by `.click()`."))

;; ### Acting on Select Elements ###
(defprotocol ISelectElement
  "Actions specific to select lists"
  (all-options [select-element] "Retrieve all options from the given select list")
  (all-selected-options [select-element] "Retrieve a seq of all selected options from the select list described by `by`")
  (deselect-option [select-element attr-val] "Deselect an option from a select list, either by `:value`, `:index` or `:text`")
  (deselect-all [select-element] "Deselect all options for a given select list. Does not leverage WebDriver method because WebDriver's isMultiple method is faulty.")
  (deselect-by-index [select-element idx] "Deselect the option at index `idx` for the select list described by `by`. Indeces begin at 0")
  (deselect-by-text [select-element text] "Deselect all options with visible text `text` for the select list described by `by`")
  (deselect-by-value [select-element value] "Deselect all options with value `value` for the select list described by `by`")  
  (first-selected-option [select-element] "Retrieve the first selected option (or the only one for single-select lists) from the given select list")
  (multiple? [select-element] "Return true if the given select list allows for multiple selections")
  (select-option [select-element attr-val] "Select an option from a select list, either by `:value`, `:index` or `:text`")
  (select-all [select-element] "Select all options for a given select list")
  (select-by-index [select-element idx] "Select an option by its index in the given select list. Indeces begin at 0.")
  (select-by-text [select-element text] "Select all options with visible text `text` in the select list described by `by`")
  (select-by-value [select-element value] "Select all options with value `value` in the select list described by `by`"))


;; ## Starting Driver/Browser ##
(def ^{:doc "Map of keywords to available WebDriver classes."}
  webdriver-drivers
  {:firefox FirefoxDriver
   :ie InternetExplorerDriver
   :chrome ChromeDriver
   :opera OperaDriver
   :htmlunit HtmlUnitDriver})

(defn new-webdriver*
  "Return a Selenium-WebDriver WebDriver instance, optionally configured to leverage a custom FirefoxProfile."
  ([browser-spec]
     (let [{:keys [browser profile] :or {browser :firefox
                                         profile nil}} browser-spec]
       (if-not profile
         (.newInstance (webdriver-drivers (keyword browser)))
         (FirefoxDriver. profile)))))

(defn new-driver
  "Start a new Driver instance. The `browser-spec` can include `:browser`, `:profile`, and `:cache-spec` keys.

   The `:browser` can be one of `:firefox`, `:ie`, `:chrome` or `:htmlunit`.
   The `:profile` should be an instance of FirefoxProfile you wish to use.
   The `:cache-spec` can contain `:strategy`, `:args`, `:include` and/or `:exclude keys. See documentation on caching for more details."
  ([browser-spec]
     (let [{:keys [browser profile cache-spec] :or {browser :firefox
                                                    profile nil
                                                    cache-spec {}}} browser-spec]
       (init-driver {:webdriver (new-webdriver* {:browser browser
                                                 :profile profile})
                     :cache-spec cache-spec}))))

(defn start
  "Shortcut to instantiate a driver, navigate to a URL, and return the driver for further use"
  ([browser-spec url]
     (let [driver (new-driver browser-spec)]
       (get-url driver url)
       driver)))

;; Borrowed from core Clojure
(defmacro with-driver
  "bindings => [name init ...]

  Evaluates body in a try expression with names bound to the values
  of the inits, and a finally clause that calls (.close name) on each
  name in reverse order."
  [bindings & body]
  (assert-args
     (vector? bindings) "a vector for its binding"
     (even? (count bindings)) "an even number of forms in binding vector")
  (cond
    (= (count bindings) 0) `(do ~@body)
    (symbol? (bindings 0)) `(let ~(subvec bindings 0 2)
                              (try
                                (with-driver ~(subvec bindings 2) ~@body)
                                (finally
                                  (quit ~(bindings 0)))))
    :else (throw (IllegalArgumentException.
                   "with-driver only allows Symbols in bindings"))))

;; alias for with-driver
(defmacro with-browser
  "Alias for with-driver

  bindings => [name init ...]

  Evaluates body in a try expression with names bound to the values
  of the inits, and a finally clause that calls (.close name) on each
  name in reverse order."
  [& args]
  `(with-driver ~@args))

;; TODO: verify these functions' necessity
(defn window-handle*
  "For WebDriver API compatibility: this simply wraps `.getWindowHandle`"
  [driver]
  (.getWindowHandle driver))

(defn window-handles*
  "For WebDriver API compatibility: this simply wraps `.getWindowHandles`"
  [driver]
  (lazy-seq (.getWindowHandles driver)))

(defn other-window-handles*
  "For consistency with other window handling functions, this starred version just returns the string-based ID's that WebDriver produces"
  [driver]
  (remove #(= % (window-handle* driver))
          (doall (window-handles* driver))))

(load "core_by")

;; ##  Actions on WebElements ##
(declare execute-script)
(declare execute-script*)
(defn- browserbot
  [driver fn-name & arguments]
  (let [script (str browserbot-js/script
                    "return browserbot."
                    fn-name
                    ".apply(browserbot, arguments)")
        execute-js-fn (partial execute-script* driver script)]
    (apply execute-js-fn arguments)))

;; Implementations of the above IElement and IFormElement protocols
(load "core_element")

;; Key codes for non-representable keys
(defn key-code
  "Representations of pressable keys that aren't text. These are stored in the Unicode PUA (Private Use Area) code points, 0xE000-0xF8FF. Refer to http://www.google.com.au/search?&q=unicode+pua&btnG=Search"
  [k]
  (Keys/valueOf (.toUpperCase (name k))))

;; ## JavaScript Execution ##
(defn execute-script
  [driver js & js-args]
  (.executeScript (:webdriver driver) js (to-array js-args)))

(defn execute-script*
  "Version of execute-script that uses a WebDriver instance directly."
  [driver js & js-args]
  (.executeScript driver js (to-array js-args)))

(load "core_driver")
