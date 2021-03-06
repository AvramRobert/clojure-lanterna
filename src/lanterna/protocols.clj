(ns lanterna.protocols)

;;; TODO:
;;; * graphics operations into the generic API. All these graphics
;;; ops are done via TextGraphics, for which we('ll) have an Output 
;;; implementation, and is also available via the Output protocol.
;;; So no matter whether Screen or Terminal (or even GUI), we can
;;; (1) access a fresh TextGraphics that paints natively for the context object, and,
;;; (2) use the above to perform the paint operation
;;; (3) return the original context object.
;;; This should remain reflection free.

;;; FIXME:
;;; implement for:
;;; * com.googlecode.lanterna.screen.Screen
;;; * com.googlecode.lanterna.TextGraphics
;;; ?? MultiWindowTextGUI?
;;;
(defprotocol Output
  "A protocol to generate an interface so we can find a common
  ancestor for terminals and screens, text graphics, etc.
  Allows to offer a stable, reflection-less API across potential output providers."
  (-put-char 
    [this ch [x y] {:as opts :keys [fg bg styles flush?]}]
    "writes out a single (java.lang.)Character ch on the x/y position given,
    with the specific options:
    :fg       - foreground color (keyword, cf. lanterna.constants/colors)
    :bg       - background color (keyword, cf. lanterna.constants/colors)
    :styles   - set of styles (keywords, cf. lanterna.constants/styles)
    :flush?   - if true, \"flush\" this, whatever that means
    warning: do not output control characters.
    returns self.")
  (-put-string
    [this string [x y] {:as opts :keys [fg bg styles flush?]}]
    "writes out a single (java.lang.)String string on the x/y position given,
    with the specific options:
    :fg       - foreground color (keyword, cf. lanterna.constants/colors)
    :bg       - background color (keyword, cf. lanterna.constants/colors)
    :styles   - set of styles (keywords, cf. lanterna.constants/styles)
    :flush?   - if true, \"flush\" this, whatever that means
    warning: do not include control characters (i.e., no newline either),
             multiple lines need to be put in separate calls.
    returns self.")
  (-get-cursor
    [this]
    "returns the current cursor position of the output as [x y].")
  (-set-cursor
    [this [x y]]
    "moves the cursor position to [x y].
    returns self.")
  (-flush
    [this]
    "flushes self to ensure output is being displayed.
    returns self.")
  (-clear
    [this]
    "clears self to present a blank output.
    returns self.")
  (-start
    [this]
    "make this output usable.
    returns self.")
  (-stop
    [this]
    "stop using this output.
    returns self.")
  (-get-size
    [this]
    "returns [width height] for this output")
  (-get-fg
    [this]
    "returns the foreground color as a lanterna.constants/colors entry")
  (-set-fg
    [this fg]
    "alters output to use given foreground color fg (cf. lanterna.constants/colors).
    returns self.")
  (-get-bg
    [this]
    "returns the background color as a lanterna.constants/colors entry")
  (-set-bg
    [this bg]
    "alters output to use given background color bg (cf. lanterna.constants/colors).
    returns self.")
  (-get-styles
    [this]
    "returns a set of active styles as per lanterna.constants/styles")
  (-set-styles
    [this styles]
    "sets the set of active styles (a set of keywords)
    on the output (cf. lanterna.constants/styles).
    returns self.")
  (-terminal
    [this]
    "returns access to the underlying terminal")
  (-screen
    [this]
    "returns access to the underlying screen, if available")
  (-gui
    [this]
    "returns access to the running GUI, if available")
  (-textgraphics
    [this]
    "returns a new lanterna TextGraphics instance that draws on self"))

(defprotocol Input
  "A protocol to generate an interface so we can find a common
  ancestor for terminals and screens (potentially GUIs, TextGraphics, ...).
  Allows to offer a stable, reflection-less API across potential input providers.
  Further operations on the input can be (and are) implemented using the methods herein."
  (-poll-stroke 
    [this]
    "returns (immediately) information about the next
    keystroke (a map with :key :ctrl :alt :shift keys)
    or nil if the buffer is empty.")
  (-get-stroke 
    [this]
    "returns a single key info map (bearing :key :ctrl :alt :shift
    keys) from the input.
    Blocks if there is no input available."))
