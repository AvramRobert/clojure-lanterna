(ns lanterna.terminal
  (:import 
    [com.googlecode.lanterna TerminalSize]
    [com.googlecode.lanterna.terminal DefaultTerminalFactory SimpleTerminalResizeListener
     Terminal TerminalFactory]
    [com.googlecode.lanterna.terminal.ansi CygwinTerminal UnixTerminal]
    [com.googlecode.lanterna.terminal.swing AWTTerminalFontConfiguration$BoldMode
     SwingTerminal SwingTerminalFontConfiguration TerminalEmulatorColorConfiguration 
     TerminalEmulatorDeviceConfiguration]
    [java.awt Font GraphicsEnvironment])
  (:require 
    [lanterna.constants :as c]
    [lanterna.input :as i]
    [lanterna.protocols :as prot])
  (:refer-clojure :exclude [flush]))

(defn add-resize-listener
  "Create a listener that will call the supplied fn when the terminal is resized.
  The function must take two arguments: the new number of columns and the new
  number of rows.

  The listener itself will be returned. You don't need to do anything with it,
  but you can use it to remove it later with remove-resize-listener."
  [^Terminal terminal listener-fn]
  (let [listener (proxy [SimpleTerminalResizeListener] [(.getTerminalSize terminal)]
                   (onResized [terminal ^TerminalSize newSize]
                     (listener-fn
                      terminal
                      (.getColumns newSize)
                      (.getRows newSize))))]
    (.addResizeListener terminal listener)
    listener))

(defn remove-resize-listener
  "Remove a resize listener from the given terminal."
  [^Terminal terminal listener]
  (.removeResizeListener terminal listener))

(defn get-available-fonts 
  "returns a set of available font family names"
  []
  (set (.getAvailableFontFamilyNames
        (GraphicsEnvironment/getLocalGraphicsEnvironment))))

(defn- get-font 
  "returns the font if it is available, or nil else"
  [font]
  (let [available (get-available-fonts)]
    (available font)))

(defn- filter-fonts 
  "filters a seq of font names to those which are available on the system."
  [fonts]
  (filter get-font fonts))

(defn- fallback-fonts 
  "returns a sane (?) default collection of usable monospace fonts"
  []
  (filter-fonts ["Droid Sans Mono" "Inconsolata" "DejaVu Sans Mono" "Consolas" "Monospaced" "Mono"]))

(defn- ^TerminalFactory get-factory 
  "produces a terminal factory with various options setup:
   :title     - window title, if applicable
   :cols      - amount of columns requested
   :rows      - amount of rows requested
   :charset   - I/O character set requested
   :font      - collection of font (in order of priority)
   :font-size - font-size to use
   :palette   - color palette to use
  returns appropriately setup factory."
  [kind
                    {:as opts
                     :keys [title cols rows charset font font-size palette]
                     :or {title "terminal"
                          cols 80
                          rows 24
                          charset :utf-8
                          font []
                          font-size 14
                          palette :mac-os-x}}]
  (let [filtered (filter-fonts font)
        fonts (if-not (empty? filtered) filtered (fallback-fonts))]
    (doto (new DefaultTerminalFactory System/out System/in (c/charsets charset))
      (.setForceTextTerminal (= :text kind))
      (.setForceAWTOverSwing (= :awt kind))
      (.setTerminalEmulatorTitle title)
      (.setTerminalEmulatorFontConfiguration
        (new SwingTerminalFontConfiguration
             true
             AWTTerminalFontConfiguration$BoldMode/EVERYTHING
             (into-array Font (map #(new Font % Font/PLAIN font-size) fonts))))
        (.setInitialTerminalSize (new TerminalSize cols rows))
        (.setTerminalEmulatorColorConfiguration (TerminalEmulatorColorConfiguration/newInstance (c/palettes palette)))
        (.setTerminalEmulatorDeviceConfiguration (new TerminalEmulatorDeviceConfiguration))))) ; TODO: Allow customization


(defn get-terminal
  "Get a terminal object.

  kind can be one of the following:

  :auto   - Use a Swing terminal if a windowing system is present, or use a text
            based terminal appropriate to the operating system.
  :awt    - Force an AWT terminal
  :cygwin - Force a Cygwin console terminal.
  :swing  - Force a Swing terminal
  :text   - Force a text-based (i.e. non-Swing) terminal.  Try to guess the
            appropriate kind of terminal (UNIX/Cygwin) by the OS.
  :unix   - Force a UNIX console terminal.

  Options can contain one or more of the following keys:

  :title   - The name of the terminal window (default \"terminal\")
  :cols    - Width of the desired terminal in characters (default 80).
  :rows    - Height of the desired terminal in characters (default 24).
  :charset - Charset of the desired terminal. Can be any of
             (keys lanterna.constants/charsets) (default :utf-8).
  :resize-listener - A function to call when the terminal is resized.  This
                     function should take two parameters: the new number of
                     columns, and the new number of rows.
  :font      - Font to use. single string, or collection of strings.
               Use (lanterna.terminal/get-available-fonts) to see your options.
               Will fall back to a basic monospaced font if none of the given
               names are available.
  :font-size - Font size (default 14).
  :palette   - Color palette to use. Can be any of
               (keys lanterna.constants/palettes) (default :mac-os-x).

  The console terminal will ignore rows and columns and fonts and colors.

  The Swing terminal will start out at this size but can be resized later by the
  user, and will ignore the charset entirely."
  ([] (get-terminal :auto {}))
  ([kind] (get-terminal kind {}))
  ([kind {:as opts
          :keys [title cols rows charset resize-listener font font-size palette]
          :or {title "terminal"
               cols 80
               rows 24
               charset :utf-8
               resize-listener nil
               font ["Droid Sans Mono" "DejaVu Sans Mono" "Consolas" "Monospaced" "Mono"]
               font-size 14
               palette :mac-os-x}}]
   (let [fonts (if (coll? font) font [font])
         factory (get-factory kind (assoc opts :font fonts))
         terminal (case kind
                    :unix (UnixTerminal. System/in System/out (c/charsets charset))
                    :cygwin (CygwinTerminal. System/in System/out (c/charsets charset))
                    (.createTerminal factory))]
     (when resize-listener
       (add-resize-listener terminal resize-listener))
     terminal)))

(defn start
  "Puts the terminal into private / alternate screen mode.
  This typically results in a fresh screen space."
  [^Terminal terminal]
  (doto terminal
    .enterPrivateMode))

(defn stop
  "Returns from private / alternate screen mode. This may or
  may not hide the private screen content and return the display
  back to the state it was before entering private mode"
  [^Terminal terminal]
  (doto terminal 
    .exitPrivateMode))

(defn flush
  "Flushes the terminal. This may be flushing buffered I/O on the
  underlying ByteInput/OutputStreams, or a no-op for specific
  terminal implementations. Without flushing, there is no guarantee
  draw operations since the last flush will be displayed."
  [^Terminal terminal]
  (doto terminal
    .flush))

(defmacro in-terminal
  "Enter the terminal's private mode, perform the body, and stop the terminal afterward."
  [terminal & body]
  `(do
     (start ~terminal)
     (try ~@body
          (finally (stop ~terminal)))))

(defn get-size
  "Return the current size of the terminal as [cols rows]."
  [^Terminal terminal]
  (let [size (.getTerminalSize terminal)
        cols (.getColumns size)
        rows (.getRows size)]
    [cols rows]))

(defn move-cursor
  "Move the cursor to a specific location on the screen."
  ([^Terminal terminal x y]
   (doto terminal 
     (.setCursorPosition x y)))
  ([^Terminal terminal [x y]]
   (move-cursor terminal x y)))

(defn set-style
  "Activates a single specific style for following draw operations
  on the terminal."
  [^Terminal terminal style]
  (doto terminal
    (.enableSGR (c/styles style))))

(defn remove-style
  "Deactivate a single specific style for following draw operations
  on the terminal."
  [^Terminal terminal style]
  (doto terminal
    (.disableSGR (c/styles style))))

(defn reset-styles
  "Reset all styles and return colors to their defaults for following
  draw operations on the terminal"
  [^Terminal terminal]
  (doto terminal
    .resetColorAndSGR))

(defn set-fg-color 
  "Sets the foreground color for following 
  draw operation on the terminal to the given color"
  [^Terminal terminal color]
  (doto terminal
    (.setForegroundColor (c/colors color))))

(defn set-bg-color 
  "Sets the background color for following 
  draw operation on the terminal to the given color"
  [^Terminal terminal color]
  (doto terminal
    (.setBackgroundColor (c/colors color))))

(defn put-character
  "Draw the character at the current cursor location. If x and y are given,
  moves the cursor there first. Moves the cursor one character to the right
  afterwards, so a sequence of calls will output next to each other. If
  flush? is true (or not given), immediately flush the output terminal."
  ([^Terminal terminal ch flush?]
   (.putCharacter terminal ch)
   (when flush? (.flush terminal))
   terminal)
  ([^Terminal terminal ch]
   (put-character terminal ch true))
  ([^Terminal terminal ch x y flush?]
   (move-cursor terminal x y)
   (put-character terminal ch)
   (when flush? (.flush terminal))
   terminal)
  ([^Terminal terminal ch x y]
   (put-character terminal ch x y true)))

(defn put-string
  "Draw the string at the current cursor location. If x and y are given, moves
  the cursor there first. The cursor will end up at the position directly after
  the string."
  ([^Terminal terminal s]
   (doseq [c s] (put-character terminal c false))
   (.flush terminal)
   terminal)
  ([^Terminal terminal ^String s ^Integer x ^Integer y]
   (move-cursor terminal x y)
   (put-string terminal s))
  ([^Terminal terminal ^String s ^Integer x ^Integer y
    {:as opts
     :keys [fg bg styles]
     :or {fg :default
          bg :default
          styles #{}}}]
   (doseq [style styles] 
     (set-style terminal style))
   (-> terminal
     (set-fg-color fg)
     (set-bg-color bg)
     (put-string s x y)
     (reset-styles))))

(defn clear
  "Clear the terminal. The cursor will be at 0,0 afterwards, 
  and the terminal flushed."
  [^Terminal terminal]
  (doto terminal
    .clearScreen
    (move-cursor 0 0)
    .flush))

(defn get-cursor
  "Return the cursor position as [x y]."
  [^Terminal terminal]
  (let [pos (.getCursorPosition terminal)]
    [(.getColumn pos) (.getRow pos)]))

(extend-type Terminal prot/Output
  (-write-char [this ch [x y] {:as opts :keys [fg bg styles flush?]}]
    (put-character this ch x y flush?))
  (-write-string [this string [x y] {:as opts :keys [fg bg styles flush?]}]
    (put-string this string x y opts))
  (-get-cursor [this] (get-cursor this))
  (-set-cursor [this [x y]] (move-cursor this x y))
  (-flush [this] (flush this))
  (-clear [this] (clear this))
  (-start [this] (start this))
  (-stop [this] (stop this))
  (-get-size [this] (get-size this))
  (-get-fg [this])
  (-set-fg [this fg])
  (-get-bg [this])
  (-set-bg [this bg])
  (-get-styles [this])
  (-set-styles [this styles])
  (-terminal [this] this)
  (-screen [this] nil)
  (-gui [this] nil)
  (-textgraphics [this] (.newTextGraphics this)))
