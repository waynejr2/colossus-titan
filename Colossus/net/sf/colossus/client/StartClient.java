package net.sf.colossus.client;


import java.util.*;
import java.rmi.*;
import com.werken.opt.Options;
import com.werken.opt.Option;
import com.werken.opt.CommandLine;

import net.sf.colossus.server.IRMIServer;
import net.sf.colossus.util.Log;


/**
 *  Startup code for RMI Client
 *  @version $Id$
 *  @author David Ripton
 */


public class StartClient
{
    private static String playerName = "RemoteTest";
    private static String hostname = "localhost";


    private static void usage(Options opts)
    {
        Log.event("Usage: java net.sf.colossus.client.StartClient [options]");
        Iterator it = opts.getOptions().iterator();
        while (it.hasNext())
        {
            Option opt = (Option)it.next();
            Log.event(opt.toString());
        }
    }


    public static void main(String [] args)
    {
        // This is a werken Options, not a util Options.
        Options opts = new Options();
        CommandLine cl = null;

        try
        {
            opts.addOption('h', "help", false, "Show options help");
            opts.addOption('p', "player", true, "Player name");
            opts.addOption('s', "server", true, "Server host name");

            cl = opts.parse(args);
        }
        catch (Exception ex)
        {
            // TODO Clean up the output.
            ex.printStackTrace();
            return;
        }

        if (cl.optIsSet('h'))
        {
            usage(opts);
            return;
        }
        if (cl.optIsSet('p'))
        {
            playerName = cl.getOptValue('p');
        }
        if (cl.optIsSet('s'))
        {
            hostname = cl.getOptValue('s');
        }


        if (System.getSecurityManager() == null) 
        {
            System.setSecurityManager(new RMISecurityManager());
        }
        try 
        {
            String name = "//" + hostname + "/Colossus";
            IRMIServer server = (IRMIServer)Naming.lookup(name);
            Client client = new Client(server, playerName, false);
            client.attachToServer();
        } 
        catch (Exception e) 
        {
            Log.error("StartClient exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
