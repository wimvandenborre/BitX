This is the beginning of a Clyphx system for Bitwig

If you don't want to compile and try it out just download the extension from the Download me directory. Unzip the whole folder into Bitwig Studio/Extensions

The script works on Mac and Windows, just the display window works on mac silicon atm. The app is signed but if you experience issues go to system settings security to allow the app to run.



Multiple commands per line are now possible: ()BPM 120 ()SMW Hello World ()SPN Notification Message

Available commands at this time:

()BPM "bpm" -> changes the bpm -> ()BPM 124:8 transitions to the bpm in number of bars

()SMW "message" -> Show Message Window -> displays a message in a popupwindow

()LDR "presetname" -> Load Drum Rack -> Replaces your existing drum rack in second position, the drum rack presets need to be in your /Library/Presets/Drum Machine folder

()LIR "presetname" -> Load Instrument Rack -> will select the preset in an instrument rack. This is a Bitwig preset you save from any plugin you want to use.
()LIR "presetname:5" -> the second parameter will select the remotes controls page of your selected device.

    You can set the position of the the instrument rack in the preferences

()SPN "Message" -> Show a popup Message that dissapears after a few seconds

()SCF 1:5:9 -> Set Channel filter -> Will get the first channel filter on the track and set the channels/ 

()SNF D1:E5 ->  Set Note Filter -> Change the key range for the note filter device.

()SNT octave:coarse:fine -> Set Note Transpose -> for example: ()SNT 2:30:-40

()OSC Sends an OSC message. Usage: ()OSC /address arg1 arg2 ..."

()STS Set Time Signature ()STS 4:8
