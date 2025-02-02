This is the beginning of a Clyphx system for Bitwig

If you don't want to compile and try it out just download the extension from the ${bitwig.extension.directory} directory

1) For the displaywindow to work you need to be on a Bitwig version before 5.2 (this broke, hopefully the devs will fix this)
In a clipname type for example ()SMW Hello world -> This will display the message in a popupwindow

2) On an instrument track put a drumrack on the second position. (put an fx selector for example before it)
Now on that track write in a clipname ()LDR nameofyourdrumrackpreset
This will change the drumrack for that track

3) Set the BPM with ()BPM 128 for example 60.04 (doubles work also)

4) On an instrument track put an instrument selector on the second position (put an fx selector for example before it)
  addd some presets that you create from plugins in the selector. Then in a clipname on that track use ()LIR nameofpreset -> This will load the requested preset.

5) Multiple commands per line are now possible: ()BPM 120 ()SMW Hello World ()SPN Notification Message
  
Available commands at this time:

()BMP "bpm" -> changes the bpm

()SMW "message" -> Show Message Window -> displays a message in a popupwindow

()LDR "presetname" -> Load Drum Rack -> Replaces your existing drum rack in second position, the drum rack presets need to be in your /Library/Presets/Drum Machine folder

()LIR "presetname" -> Load Instrument Rack -> will select the preset in an instrument rack. This is a Bitwig preset you save from any plugin you want to use.

()SPN "Message" -> Show a popup Message that dissapears after a few seconds

