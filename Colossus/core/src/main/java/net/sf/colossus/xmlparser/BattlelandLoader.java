package net.sf.colossus.xmlparser;


import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.colossus.client.BattleHex;
import net.sf.colossus.variant.HazardTerrain;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;


/**
 * BattlelandLoader loads the battle hex data
 * @author Romain Dolbeau
 * @version $Id$
 */
public class BattlelandLoader
{
    private static final Logger LOGGER = Logger
        .getLogger(BattlelandLoader.class.getName());

    /** hold the list of label for the startlist */
    private java.util.List<String> startlist = null;

    /** is the terrain a Tower ? */
    private boolean isTower = false;

    /** optional subtitle for the Battlelands */
    private String subtitle = null;

    // we need to cast since JDOM is not generified
    @SuppressWarnings("unchecked")
    public BattlelandLoader(InputStream batIS, BattleHex[][] h)
    {
        SAXBuilder builder = new SAXBuilder();
        try
        {
            Document doc = builder.build(batIS);
            Element root = doc.getRootElement();
            // @todo check XML definition, here was: root.getAttributeValue("terrain");
            isTower = root.getAttribute("tower").getBooleanValue();
            subtitle = root.getAttributeValue("subtitle");

            List<Element> hexlist = root.getChildren("battlehex");
            for (Iterator<Element> it = hexlist.iterator(); it.hasNext();)
            {
                Element el = it.next();
                handleHex(el, h);
            }
            Element startlistEl = root.getChild("startlist");
            if (startlistEl != null)
            {
                // towi: the DTD "battlehex" definitions clashed.
                //    renamed to "battlehexref"
                List<Element> startlistHexes = startlistEl
                    .getChildren("battlehexref");
                if (startlistHexes.size() == 0)
                {
                    // support old format with warning
                    startlistHexes = startlistEl.getChildren("battlehex");
                    if (startlistHexes.size() > 0)
                    {
                        LOGGER.log(Level.WARNING,
                            "DEPRECATION WARNING: in 'startlist' use "
                                + "'battlehexref' instead of 'battlehex'!");
                    }
                }

                for (Iterator<Element> it = startlistHexes.iterator(); it
                    .hasNext();)
                {
                    Element el = it.next();
                    handleStartlistHex(el);
                }
            }
        }
        catch (JDOMException ex)
        {
            // towi TODO : is it really good to swallow the exception? 
            LOGGER.log(Level.SEVERE, "JDOM exception caught", ex);
        }
        catch (IOException ex)
        {
            // towi TODO: is it really good to swallow the exception? 
            LOGGER.log(Level.SEVERE, "IO exception caught", ex);
        }
    }

    // we need to cast since JDOM is not generified
    @SuppressWarnings("unchecked")
    private void handleHex(Element el, BattleHex[][] h) throws JDOMException
    {
        int xpos = el.getAttribute("x").getIntValue();
        int ypos = el.getAttribute("y").getIntValue();
        BattleHex hex = h[xpos][ypos];

        String terrain = el.getAttributeValue("terrain");
        hex.setTerrain(HazardTerrain.getTerrainByName(terrain));

        int elevation = el.getAttribute("elevation").getIntValue();
        hex.setElevation(elevation);

        List<Element> borders = el.getChildren("border");
        for (Iterator<Element> it = borders.iterator(); it.hasNext();)
        {
            Element border = it.next();
            int number = border.getAttribute("number").getIntValue();
            char type = border.getAttributeValue("type").charAt(0);
            hex.setHexside(number, type);
        }
    }

    private void handleStartlistHex(Element el)
    {
        String label = el.getAttributeValue("label");
        if (startlist == null && label != null)
        {
            startlist = new ArrayList<String>();
        }
        if (label != null)
        {
            startlist.add(label);
        }
    }

    public java.util.List<String> getStartList()
    {
        return startlist;
    }

    public String getSubtitle()
    {
        return subtitle;
    }

    public boolean isTower()
    {
        return isTower;
    }
}
