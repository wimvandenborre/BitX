package com.personal;
import java.util.UUID;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

public class BitXExtensionDefinition extends ControllerExtensionDefinition
{
   private static final UUID DRIVER_ID = UUID.fromString("a7f7b82e-0ce6-47e0-b1b7-be90d7257e7b");
   
   public BitXExtensionDefinition()
   {
   }

   @Override
   public String getName()
   {
      return "BitX";
   }
   
   @Override
   public String getAuthor()
   {
      return "Wim Van den Borre";
   }

   @Override
   public String getVersion()
   {
      return "0.1";
   }

   @Override
   public UUID getId()
   {
      return DRIVER_ID;
   }
   
   @Override
   public String getHardwareVendor()
   {
      return "Per-Sonal";
   }
   
   @Override
   public String getHardwareModel()
   {
      return "BitX";
   }

   @Override
   public int getRequiredAPIVersion()
   {
      return 18;
   }

   @Override
   public int getNumMidiInPorts()
   {
      return 0;
   }

   @Override
   public int getNumMidiOutPorts()
   {
      return 0;
   }

   @Override
   public void listAutoDetectionMidiPortNames(final AutoDetectionMidiPortNamesList list, final PlatformType platformType)
   {
   }

   @Override
   public BitXExtension createInstance(final ControllerHost host)
   {
      return new BitXExtension(this, host);
   }
}
