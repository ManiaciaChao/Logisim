package com.cburch.logisim.std.io;

/* Copyright (c) 2014, PUC-Minas. License information is located in the
 * com.cburch.logisim.Main source code and at www.cburch.com/logisim/. */


import java.awt.Color;
import java.awt.Graphics;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.sound.sampled.*;
import javax.swing.JOptionPane;

import com.cburch.logisim.data.*;
import com.cburch.logisim.instance.*;
import com.cburch.logisim.instance.Port;

import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.StringGetter;

public class Buzzer extends InstanceFactory
{
   public static final Attribute<Integer> FREQ_ATTR = Attributes.forIntegerRange("Frequency"    , 100,  5000);
  
   public Buzzer()
   {
      super("Buzzer");
/*      
      setPorts(new Port[] {
               new Port( 0, 0, Port.INPUT , 1)
//               ,new Port(40, 0, Port.OUTPUT, StdAttr.WIDTH)
              });
*/          
      Port port = new Port ( 0, 0, Port.INPUT, 1 );
      String foo = "multiplexerEnableTip";
      port.setToolTip(Strings.getter(foo));
      
      setPorts(new Port[] { port });

      setAttributes ( new Attribute[] { FREQ_ATTR, }, 
                      new Object[]    { new Integer(500)
                    });
      
      setOffsetBounds ( Bounds.create(0, -20, 40, 40) );
   }

	@Override
	@SuppressWarnings("unchecked")
   public void propagate(InstanceState state)
   {
      Data d = (Data) state.getData( );
      
      if ( d == null )
         state.setData ( d = new Data ( false ) );
     
      d.is_on = state.getPort(0) == Value.TRUE;
     
      if ( d.is_on )
         d.still_alive = true;
     
      int  freq = (state.getAttributeValue(FREQ_ATTR)).intValue();
      
      
      if ( freq != d.freq)
      {
         d.freq = freq;
         d.sound_changed = true;
      }
     
      if ( ! d.thread.isAlive( ) && d.is_on && d.sound_changed )
         d.StartThread( );
      
//      Value out;
//      out = Value.createKnown ( BitWidth.create(32), len );
//      state.setPort(1, out, out.getWidth() + 1);
   }

   public void paintInstance(InstancePainter painter)
   {
      Graphics g = painter.getGraphics();
      Bounds b   = painter.getBounds();
      
      int x = b.getX();
      int y = b.getY();
      
      g.setColor(Color.BLACK);
      
      for(int k = 0; k <= 20; k += 5)
         g.drawOval((x + 20) - k, (y + 20) - k, k * 2, k * 2);
   
      painter.drawPort(0);
//      painter.drawPort(1);

      Data d = (Data) painter.getData( );
      if ( d != null && d.is_on )
      {
         d.still_alive = true;
         if ( ! d.thread.isAlive( ) )
            d.StartThread( );
      }
   }

   public void paintGhost(InstancePainter painter)
   {
      Bounds b = painter.getBounds();
      Graphics g = painter.getGraphics();
      g.setColor(Color.GRAY);
      g.drawOval(b.getX(), b.getY(), 40, 40);
   }
   protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
		if (attr ==FREQ_ATTR) {
			instance.recomputeBounds();
			Data d=new Buzzer.Data(true);
			d.StartThread();
		}
   }
   private static class Data
       implements InstanceData
   {
      public void StartThread()
      {
         thread = new Thread(
                  new Runnable() {
               
                  public void run()
                  {
                     SourceDataLine line = null;
                     AudioFormat format 
                     = new AudioFormat(11025F, 8, 1, true, false);
                     DataLine.Info info 
                     = new DataLine.Info(SourceDataLine.class, format);
                  
                     
                     try
                     {
                        line = (SourceDataLine) AudioSystem.getLine(info);
                        line.open (format, 11025);
                     }
                     catch ( Exception e )
                     {
                        StringWriter sw = new StringWriter( );
                        JOptionPane.showMessageDialog
                        ( null, 
                          sw.getBuffer().toString(), 
                          "ERROR (Buzzer): Could not initialise audio", 
                          0);
                        return;
                     }
                     line.start( );
                     byte audioData[] = new byte[1102];
                     sound_changed = true;
                  
                     int i = 0;
                  
                     do
                     {
                        if ( sound_changed )
                        {
                           sound_changed = false;
                           double step = 11025D / (double)freq;
                           double n = step;
                           byte val = 127;
                           
                           for (int k = 0; k < audioData.length; k++ )
                           {
                              n--;
                              if( n < 0.0D )
                              {
                                 n += step;
                                 val = (byte)(-val);
                              }
                              audioData[k] = val;
                           }
                        }
                        if(is_on)
                        {
                            line.write(audioData, 0, audioData.length);
                        }
                        try
                        {
                            Thread.sleep(99L);
                        }
                        catch(Exception e)
                        {
                            break;
                        }
                        if(++i != 10)
                        {
                            continue;
                        }
                        if(!still_alive)
                        {
                            break;
                        }
                        still_alive = false;
                        i = 0;
                     } 
                     while ( true );
                     line.stop ( );
                     line.close ( );
                  }
               
               }
         );
         thread.start ( );
      }
   
      public Object clone ( )
      {
         return new Data( is_on );
      }
   
      public volatile boolean is_on;
      public volatile int     freq;
      public volatile boolean sound_changed;
      public volatile boolean still_alive;
      public Thread thread;
   
      public Data ( boolean b )
      {
         is_on = false;
         freq  = 500;
         sound_changed = true;
         still_alive   = true;
         is_on = b;
         StartThread ( );
      }
   
   }

}