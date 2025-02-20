This is the beginning of a Clyphx system for Bitwig

If you don't want to compile and try it out just download the extension from the ${bitwig.extension.directory} directory. Unzip the whole folder into Bitwig Studio/Extensions

1) The displaywindow is now in JavaFX and runs only on mac silicon atm. Go to system settings security to allow the java window to run.

2) On an instrument track put a drumrack on the second position. (put an fx selector for example before it)
Now on that track write in a clipname ()LDR nameofyourdrumrackpreset
This will change the drumrack for that track

3) Set the BPM with ()BPM 128 for example 60.04 (doubles work also)

4) On an instrument track put an instrument selector on the second position (put an fx selector for example before it)
  addd some presets that you create from plugins in the selector. Then in a clipname on that track use ()LIR nameofpreset -> This will load the requested preset.
  You can also add a second argument pagenumber, this will select the remotes controls page of the device you loaded. use ":" as seperator. -> for example:
  ()LIR nameofpreset:5

6) Multiple commands per line are now possible: ()BPM 120 ()SMW Hello World ()SPN Notification Message

7) Channel filter and Note range commands are availabe, see below
  
Available commands at this time:

()BMP "bpm" -> changes the bpm

()SMW "message" -> Show Message Window -> displays a message in a popupwindow

()LDR "presetname" -> Load Drum Rack -> Replaces your existing drum rack in second position, the drum rack presets need to be in your /Library/Presets/Drum Machine folder

()LIR "presetname" -> Load Instrument Rack -> will select the preset in an instrument rack. This is a Bitwig preset you save from any plugin you want to use.
()LIR "presetname:5" -> the second parameter will select the remotes controls page of your selected device.

()SPN "Message" -> Show a popup Message that dissapears after a few seconds

()SCF channelnumbers -> Set Channel filter. Will get the first channel filter on the track and set the channels/ ()SCF 1:5:9 for example, you can set one ore more channels

()CNF D1:E5 -> Change the key range for the note filter device.

