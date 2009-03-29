package net.sf.colossus.gui;



import java.awt.Color;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.JFrame;

import net.sf.colossus.client.IOptions;
import net.sf.colossus.game.Player;
import net.sf.colossus.guiutil.KDialog;


/**
 * Class PickMarker allows a player to pick a legion marker.
 * @version $Id$
 * @author David Ripton
 */

final class PickMarker extends KDialog
{
    private final List<Marker> markers = new ArrayList<Marker>();
    private final SaveWindow saveWindow;
    private String markerId;

    PickMarker(JFrame parentFrame, Player owner, Set<String> markerIds,
        IOptions options)
    {
        super(parentFrame, owner.getName() + ": Pick Legion Marker", true);

        markerId = null;

        if (markerIds.isEmpty())
        {
            cleanup(null);
        }

        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                cleanup(null);
            }

        });
        Container contentPane = getContentPane();

        int numAvailable = markerIds.size();
        contentPane.setLayout(new GridLayout(0, Math.min(numAvailable, 6)));

        pack();
        setBackground(Color.lightGray);

        Iterator<String> it = markerIds.iterator();
        while (it.hasNext())
        {
            String markerId = it.next();
            final Marker marker = new Marker(4 * Scale.get(), markerId);
            markers.add(marker);
            contentPane.add(marker);
            marker.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    cleanup(marker.getId());
                }
            });
        }

        pack();
        saveWindow = new SaveWindow(options, "PickMarker");
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

    private String getMarkerId()
    {
        return markerId;
    }

    static synchronized String pickMarker(JFrame parentFrame, Player owner,
        Set<String> markerIds, IOptions options)
    {
        PickMarker pm = new PickMarker(parentFrame, owner, markerIds, options);
        return pm.getMarkerId();
    }

    /** Pass the chosen marker id, or null if none are available or
     *  the player aborts the selection. */
    private void cleanup(String pickedMarkerId)
    {
        saveWindow.saveLocation(getLocation());
        markerId = pickedMarkerId;
        dispose();
    }
}
