package net.sf.colossus.client;


import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;

import net.sf.colossus.game.Legion;
import net.sf.colossus.server.Constants;
import net.sf.colossus.util.KDialog;
import net.sf.colossus.xmlparser.TerrainRecruitLoader;


/**
 * Class SplitLegion allows a player to split a Legion into two Legions.
 * @version $Id$
 * @author David Ripton
 */

final class SplitLegion extends KDialog implements MouseListener,
    ActionListener, WindowListener
{
    private final List<Chit> oldChits = new ArrayList<Chit>(8);
    private final List<Chit> newChits = new ArrayList<Chit>(8);

    private final Marker oldMarker;
    private final Marker newMarker;

    private Client client;
    private static boolean active;

    /** new marker id,creature1,creature2... */
    private static String results;

    private final Box oldBox;
    private final Box newBox;
    private final Box buttonBox;

    private final JButton button1;
    private final JButton button2;

    private final int totalChits;
    private final int scale;

    private final SaveWindow saveWindow;

    private SplitLegion(Client client, Legion parent, String selectedMarkerId)
    {
        super(client.getBoard().getFrame(), client.getOwningPlayer().getName()
            + ": Split Legion " + parent, true);

        Container contentPane = getContentPane();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));

        this.client = client;

        if (selectedMarkerId == null)
        {
            // TODO this should probably just throw an exception or
            // even use an assert statement
            oldMarker = null;
            newMarker = null;
            oldBox = null;
            newBox = null;
            buttonBox = null;
            button1 = null;
            button2 = null;
            scale = 0;
            totalChits = 0;
            saveWindow = null;
            dispose();
            return;
        }

        setBackground(Color.lightGray);

        addMouseListener(this);
        addWindowListener(this);

        scale = 4 * Scale.get();

        oldBox = new Box(BoxLayout.X_AXIS);
        contentPane.add(oldBox);

        newBox = new Box(BoxLayout.X_AXIS);
        contentPane.add(newBox);

        buttonBox = new Box(BoxLayout.X_AXIS);
        contentPane.add(buttonBox);

        oldMarker = new Marker(scale, parent.getMarkerId());
        oldBox.add(oldMarker);
        oldBox.add(Box.createRigidArea(new Dimension(scale / 4, 0)));
        oldBox.add(Box.createHorizontalGlue());

        List<String> imageNames = client.getLegionImageNames(parent);
        totalChits = imageNames.size();

        Iterator<String> it = imageNames.iterator();
        while (it.hasNext())
        {
            String imageName = it.next();
            Chit chit = new Chit(scale, imageName);
            oldChits.add(chit);
            oldBox.add(chit);
            chit.addMouseListener(this);
        }

        newMarker = new Marker(scale, selectedMarkerId);
        newBox.add(newMarker);
        newBox.add(Box.createRigidArea(new Dimension(scale / 4, 0)));
        newBox.add(Box.createHorizontalGlue());

        // Add chit-sized invisible spacers.
        for (int i = 0; i < totalChits; i++)
        {
            newBox.add(Box.createRigidArea(new Dimension(scale, scale)));
        }

        button1 = new JButton("Done");
        button1.setEnabled(false);
        button1.setMnemonic(KeyEvent.VK_D);
        button1.addActionListener(this);
        buttonBox.add(button1);

        button2 = new JButton("Cancel");
        button2.setMnemonic(KeyEvent.VK_C);
        button2.addActionListener(this);
        buttonBox.add(button2);

        pack();
        saveWindow = new SaveWindow(client.getOptions(), "SplitLegion");
        Point location = saveWindow.loadLocation();
        if (location == null)
        {
            centerOnScreen();
        }
        else
        {
            setLocation(location);
        }
        setVisible(true);
    }

    /** Return childMarkerId,splitCreature1,splitCreature2,etc. */
    static String splitLegion(Client client, Legion parent,
        String selectedMarkerId)
    {
        if (!active)
        {
            active = true;
            new SplitLegion(client, parent, selectedMarkerId);
            active = false;
            return results;
        }
        return null;
    }

    /** Move a chit to the end of the other line. */
    private void moveChitToOtherLine(List<Chit> fromChits, List<Chit> toChits,
        Container fromBox, Container toBox, int oldPosition)
    {
        Chit chit = fromChits.remove(oldPosition);
        fromBox.remove(chit);
        fromBox.add(Box.createRigidArea(new Dimension(scale, 0)));

        toBox.remove(toBox.getComponentCount() - 1);
        toChits.add(chit);
        // Account for the marker and the spacer.
        toBox.add(chit, toChits.size() + 1);

        button1.setEnabled(isSplitLegal(false));

        pack();
        repaint();
    }

    /** Return true if the split is legal.  Each legion must have
     *  height >= 2.  If this was an initial split, each legion
     *  must have height == 4 and one lord. */
    private boolean isSplitLegal(boolean showMessage)
    {
        if (oldChits.size() < 2 || newChits.size() < 2)
        {
            if (showMessage)
            {
                client.showMessageDialog("Legion too short.");
            }
            return false;
        }
        if (oldChits.size() + newChits.size() == 8)
        {
            if (oldChits.size() != newChits.size())
            {
                if (showMessage)
                {
                    client.showMessageDialog("Initial split must be 4-4.");
                }
                return false;
            }

            int numLords = 0;
            Iterator<Chit> it = oldChits.iterator();
            while (it.hasNext())
            {
                Chit chit = it.next();
                String id = chit.getId();
                if (id.startsWith(Constants.titan)
                    || id.equals(TerrainRecruitLoader.getPrimaryAcquirable()))
                {
                    numLords++;
                }
            }
            if (numLords != 1)
            {
                if (showMessage)
                {
                    client.showMessageDialog("Each stack must have one lord.");
                }
                return false;
            }
        }
        return true;
    }

    private void cancel()
    {
        results = null;
        dispose();
    }

    private void returnSplitResults()
    {
        StringBuffer buf = new StringBuffer();
        Iterator<Chit> it = newChits.iterator();
        while (it.hasNext())
        {
            Chit chit = it.next();
            String creatureName = chit.getId();
            if (creatureName.startsWith(Constants.titan))
            {
                creatureName = Constants.titan;
            }
            buf.append(creatureName);
            if (it.hasNext())
            {
                buf.append(",");
            }
        }
        results = buf.toString();
        dispose();
    }

    @Override
    public void dispose()
    {
        if (saveWindow != null)
        {
            saveWindow.saveLocation(getLocation());
        }
        removeMouseListener(this);
        removeWindowListener(this);
        client = null;
        super.dispose();
    }

    @Override
    public void mousePressed(MouseEvent e)
    {
        Object source = e.getSource();
        int i = oldChits.indexOf(source);
        if (i != -1)
        {
            moveChitToOtherLine(oldChits, newChits, oldBox, newBox, i);
            return;
        }
        else
        {
            i = newChits.indexOf(source);
            if (i != -1)
            {
                moveChitToOtherLine(newChits, oldChits, newBox, oldBox, i);
                return;
            }
        }
    }

    @Override
    public void windowClosing(WindowEvent e)
    {
        cancel();
    }

    public void actionPerformed(ActionEvent e)
    {
        if (e.getActionCommand().equals("Done"))
        {
            if (!isSplitLegal(true))
            {
                return;
            }
            returnSplitResults();
        }
        else if (e.getActionCommand().equals("Cancel"))
        {
            cancel();
        }
    }
}
