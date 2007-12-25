package net.sf.colossus.server;


import java.awt.*;
import java.awt.event.*;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JButton;

import net.sf.colossus.server.Server;
import net.sf.colossus.util.KFrame;


/** 
 *  Simple log window for Startup progress (waiting for clients)
 *  @version $Id$
 *  @author Clemens Katzer
 */
public final class StartupProgress implements ActionListener
{
    private KFrame logFrame;
    private TextArea text;
    private Container pane;
    private Server server;
    private JButton b;
    private JCheckBox autoCloseCheckBox;

    public StartupProgress(Server server)
    {
        this.server = server;

        net.sf.colossus.webcommon.FinalizeManager.register(this, "only one");

        //Create and set up the window.
        KFrame logFrame = new KFrame("Server startup progress log");
        this.logFrame = logFrame;

        logFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        Container pane = logFrame.getContentPane();
        this.pane = pane;

        TextArea text = new TextArea("", 20, 80);
        this.text = text;
        pane.add(text, BorderLayout.CENTER);

        JButton b1 = new JButton("Abort");
        this.b = b1;
        b1.setVerticalTextPosition(JButton.CENTER);
        b1.setHorizontalTextPosition(JButton.LEADING); //aka LEFT, for left-to-right locales
        b1.setMnemonic(KeyEvent.VK_A);
        b1.setActionCommand("abort");
        b1.addActionListener(this);
        b1.setToolTipText("Click this button to abort the start process.");
        pane.add(b1, BorderLayout.SOUTH);

        this.autoCloseCheckBox = new JCheckBox(
            "Automatically close when game starts");
        autoCloseCheckBox.setSelected(true);
        pane.add(autoCloseCheckBox, BorderLayout.NORTH);

        //Display the window.
        logFrame.pack();
        logFrame.setVisible(true);
    }

    public void append(String s)
    {
        this.text.append(s + "\n");
    }

    public void setCompleted()
    {
        if (this.autoCloseCheckBox.isSelected()) {
            this.dispose();
            return;
        }
        this.text.append("OK, all clients have come in. You can close this window now.");

        JButton b2 = new JButton("Close");
        b2.setMnemonic(KeyEvent.VK_C);
        b2.setActionCommand("close");
        b2.addActionListener(this);
        b2.setToolTipText("Click this button to close this window.");

        this.pane.remove(this.b);
        this.pane.add(b2, BorderLayout.SOUTH);
        this.b = b2;
        this.logFrame.pack();
    }

    public void dispose()
    {
        if ( this.logFrame != null )
        {
            this.logFrame.dispose();
            this.logFrame = null;
        }
    }

    public void cleanRef()
    {
        this.server = null;
    }

    public void actionPerformed(ActionEvent e)
    {
        if (e.getActionCommand().equals("abort"))
        {
            // change the abort button to a QUIT button, with which
            // one could request a System.exit() if the attempted
            // "clean up everything nicely and return to GetPlayers menu"
            // fails or hangs or something ...
            JButton b3 = new JButton("QUIT");
            b3.setMnemonic(KeyEvent.VK_C);
            b3.setActionCommand("totallyquit");
            b3.addActionListener(this);
            b3.setToolTipText("Click this button to totally exit " +
                "this application.");
            this.pane.remove(this.b);
            this.pane.add(b3, BorderLayout.SOUTH);
            this.logFrame.pack();

            this.text.append("\nAbort requested, please wait...\n");
            this.server.startupProgressAbort();
        }

        // if abort fails (hangs, NPE, ... , button is a QUIT instead,
        // so user can request a System.exit() then.
        else if (e.getActionCommand().equals("totallyquit"))
        {
            this.text.append("\nQUIT - Total Exit requested, " +
                "doing System.exit() !!\n");
            this.server.startupProgressQuit();
        }

        else if (e.getActionCommand().equals("close"))
        {
            this.text.append("\nClosing...\n");
            this.dispose();
        }
    }
}
