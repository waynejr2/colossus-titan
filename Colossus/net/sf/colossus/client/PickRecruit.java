package net.sf.colossus.client;


import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

import net.sf.colossus.server.Creature;
import net.sf.colossus.util.KDialog;


/**
 * Class PickRecruit allows a player to pick a creature to recruit.
 * @version $Id$
 * @author David Ripton
 */


final class PickRecruit extends KDialog implements MouseListener,
    WindowListener, ActionListener
{
    private java.util.List recruits;   // of Creatures
    private java.util.List recruitChits = new ArrayList();
    private Marker legionMarker;
    private java.util.List legionChits = new ArrayList();
    private JFrame parentFrame;
    private static String recruit;
    private static boolean active;


    private PickRecruit(JFrame parentFrame, java.util.List recruits, 
        String hexDescription, String markerId, Client client)
    {
        super(parentFrame, client.getPlayerName() +
            ": Pick Recruit in " + hexDescription, true);

        this.parentFrame = parentFrame;
        this.recruits = recruits;
        int numEligible = recruits.size();

        addMouseListener(this);
        addWindowListener(this);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
        setBackground(Color.lightGray);
        int scale = 4 * Scale.get();

        JPanel legionPane = new JPanel();
        contentPane.add(legionPane);

        legionMarker = new Marker(scale, markerId, this, null);
        legionPane.add(legionMarker);

        java.util.List imageNames = client.getLegionImageNames(markerId);
        Iterator it = imageNames.iterator();
        while (it.hasNext())
        {
            String imageName = (String)it.next();
            Chit chit = new Chit(scale, imageName, this);
            legionChits.add(chit);
            legionPane.add(chit);
        }

        JPanel recruitPane = new JPanel();
        contentPane.add(recruitPane);

        it = recruits.iterator();
        int i = 0;
        while (it.hasNext())
        {
            JPanel vertPane = new JPanel();
            vertPane.setLayout(new BoxLayout(vertPane, BoxLayout.Y_AXIS));
            vertPane.setAlignmentY(0);
            recruitPane.add(vertPane);

            Creature recruit = (Creature)it.next();
            String recruitName = recruit.getName();
            Chit chit = new Chit(scale, recruitName, this);
            recruitChits.add(chit);

            vertPane.add(chit);
            chit.addMouseListener(this);

            int count = client.getCreatureCount(recruitName);
            JLabel countLabel = new JLabel(Integer.toString(count));
            countLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            vertPane.add(countLabel);
            i++;
        }

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(this);
        recruitPane.add(cancelButton);

        pack();
        centerOnScreen();
        setVisible(true);
        repaint();
    }


    /** Return the creature recruited, or null if none. */
    static String pickRecruit(JFrame parentFrame, java.util.List recruits,
        String hexDescription, String markerId, Client client)
    {
        recruit = null;
        if (!active)
        {
            active = true;
            new PickRecruit(parentFrame, recruits, hexDescription, 
                markerId, client);
            active = false;
        }
        return recruit;
    }


    public void mousePressed(MouseEvent e)
    {
        Object source = e.getSource();
        int i = recruitChits.indexOf(source);
        if (i != -1)
        {
            // Recruit the chosen creature.
            recruit = ((Creature)recruits.get(i)).getName();
            dispose();
        }
    }

    public void actionPerformed(ActionEvent e)
    {
        // Only action is cancel.
        dispose();
    }


    public void windowClosing(WindowEvent e)
    {
        dispose();
    }
}
