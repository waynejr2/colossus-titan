package net.sf.colossus.gui;


import net.sf.colossus.game.BattleCritter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.colossus.client.Client;
import net.sf.colossus.common.Constants;
import net.sf.colossus.game.PlayerColor;
import net.sf.colossus.util.HTMLColor;
import net.sf.colossus.variant.BattleHex;
import net.sf.colossus.variant.CreatureType;


/**
 * Class BattleUnit implements the GUI for a Titan chit representing
 * a creature on a BattleMap.
 *
 * TODO this is a pretty wild mixture of GUI code with game logic -- there
 * is no representation of the creature in battle in the model, so this GUI
 * class does all that work, too. ==> move part of it into the game package
 *
 * @author David Ripton
 */
@SuppressWarnings("serial")
public final class BattleUnit extends Chit implements BattleCritter
{
    private static final Logger LOGGER = Logger.getLogger(BattleUnit.class
        .getName());

    private final int tag;
    private final CreatureType creatureType;
    private static Font font;
    private static Font oldFont;
    private static int fontHeight;
    private int hits = 0;
    private BattleHex currentHex;
    private BattleHex startingHex;
    private boolean moved;
    private boolean struck;
    private final Color color;
    private static BasicStroke borderStroke;
    private Rectangle midRect;
    private Rectangle outerRect;
    private int strikeNumber; // Number required for successful strike.
    private int numDice; // modifier for number of Dice rolled.
    private StrikeDie strikeDie; // Graphical representation of strikeNumber.
    private StrikeDie strikeAdjDie; // representation of dice gained or lost.
    private final int scale;

    // inner scale divided by border thickness
    private static final int borderRatio = 20;
    private static boolean useColoredBorders = false;

    public BattleUnit(int scale, String id, boolean inverted, int tag,
        BattleHex currentHex, PlayerColor playerColor, Client client)
    {
        super(scale, id, inverted, client);
        if (id == null)
        {
            LOGGER.log(Level.WARNING, "Created BattleUnit with null id!");
        }
        this.scale = scale;
        this.tag = tag;
        this.currentHex = currentHex;
        this.color = HTMLColor.stringToColor(playerColor.getName() + "Colossus");

        creatureType = client.getGame().getVariant().getCreatureByName(getCreatureName());

        setBackground(Color.WHITE);
    }

    public String getCreatureName()
    {
        String id = getId();
        if (id == null)
        {
            LOGGER.log(Level.SEVERE, "Chit.getId() returned null id ?");
            return null;
        }
        else if (id.startsWith(Constants.titan))
        {
            id = Constants.titan;
        }
        if (!id.equals(getCreatureType().getName()))
        {
            LOGGER.warning("getCreatureName() gives " + id +
                    " but creatureType.getName() gives " + getCreatureType().
                    getName());
        }
        return id;
    }


    public int getTag()
    {
        return tag;
    }

    public int getHits()
    {
        return hits;
    }

    public void setHits(int hits)
    {
        this.hits = hits;
        repaint();
    }

    public boolean wouldDieFrom(int hits)
    {
        return (hits + getHits() >= getPower());
    }

    @Override
    public void setDead(boolean dead)
    {
        super.setDead(dead);
        if (dead)
        {
            setHits(0);
        }
    }

    public BattleHex getCurrentHex()
    {
        return currentHex;
    }

    public BattleHex getStartingHex()
    {
        return startingHex;
    }

    public void setHex(BattleHex hex)
    {
        this.currentHex = hex;
    }

    public void moveToHex(BattleHex hex)
    {
        startingHex = currentHex;
        currentHex = hex;
    }

    // TODO make package private
    public boolean hasMoved()
    {
        return moved;
    }

    // TODO make package private
    public void setMoved(boolean moved)
    {
        this.moved = moved;
    }

    public boolean hasStruck()
    {
        return struck;
    }

    public void setStruck(boolean struck)
    {
        this.struck = struck;
    }

    public CreatureType getCreatureType()
    {
        return creatureType;
    }

    public boolean isTitan()
    {
        return getCreatureType().isTitan();
    }

    public int getPower()
    {
        if (isTitan())
        {
            return getTitanPower();
        }
        else
        {
            return getCreatureType().getPower();
        }
    }

    public int getSkill()
    {
        return getCreatureType().getSkill();
    }

    public int getPointValue()
    {
        return getPower() * getSkill();
    }

    public boolean isRangestriker()
    {
        return getCreatureType().isRangestriker();
    }

    @Override
    public void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D)g;

        if (!isDead())
        {
            // The power and skill are drawn with a font that is one fifth the
            // size of the Chit.
            // Construct a font that is two fifths the size of the chit
            // for drawing Hits and strike die adjustments.
            // All overlays (hits, dice) have gaps of 2.
            oldFont = g2.getFont();
            String name = oldFont.getName();
            int fifthChitSize = (rect.height > rect.width ? (rect.width - 8) / 5
                : (rect.height - 8) / 5);
            int style = oldFont.getStyle();
            font = new Font(name, style, fifthChitSize * 2);
            g2.setFont(font);
            FontMetrics fontMetrics = g2.getFontMetrics();
            fontHeight = fontMetrics.getAscent();

            String hitString = Integer.toString(hits);
            int hitsFontWidth = fontMetrics.stringWidth(hitString);

            // Setup spaces to show Hits and Strike Target.
            if (strikeNumber > 0)
            {
                Rectangle strikeRect = strikeDie.getBounds();
                Point point = new Point(rect.x + rect.width - strikeRect.width
                    - 2, (inverted ? rect.y + fifthChitSize + 4 : rect.y + 2));
                strikeDie.setLocation(point);
                strikeDie.paintComponent(g2);
                if (numDice != 0)
                {
                    String diceString = numDice < 0 ? Integer
                        .toString(numDice) : "+" + Integer.toString(numDice);
                    Point dicePoint = new Point(point.x, point.y
                        + (fifthChitSize * 2) + 2);
                    strikeAdjDie.setLocation(dicePoint);
                    strikeAdjDie.paintComponent(g2);
                    g2.setColor(Color.GREEN);
                    g2.drawString(diceString, dicePoint.x, dicePoint.y
                        + strikeRect.height - 2);
                }
            }
            if (hits > 0)
            {
                Rectangle hitRect = new Rectangle(rect.x + 2, rect.y + 2
                    + (inverted ? fifthChitSize + 2 : 0), hitsFontWidth,
                    fontHeight);

                // Provide a high-contrast background for the number.
                g2.setColor(Color.WHITE);
                g2.fillRect(hitRect.x, hitRect.y, hitRect.width,
                    hitRect.height);

                // Show number of hits taken in red.
                g2.setColor(Color.RED);
                g2.drawString(hitString, hitRect.x, hitRect.y + fontHeight);
            }
            // Restore the font.
            g2.setFont(oldFont);

        }
        if (useColoredBorders)
        {
            // Draw border using player color.
            g2.setColor(color);
            g2.setStroke(borderStroke);
            g2.drawRect(midRect.x, midRect.y, midRect.width, midRect.height);
            g2.setColor(Color.BLACK);
            g2.setStroke(oneWide);
            g2.drawRect(outerRect.x, outerRect.y, outerRect.width,
                outerRect.height);
        }
    }

    @Override
    public void setLocation(Point point)
    {
        outerRect.setLocation(point);
        setBounds(outerRect);
    }

    @Override
    public boolean contains(Point point)
    {
        return outerRect.contains(point);
    }

    @Override
    public Rectangle getBounds()
    {
        return outerRect;
    }

    @Override
    public void setBounds(Rectangle outerRect)
    {
        this.outerRect = outerRect;
        int innerScale = (int)(outerRect.width / (1.0 + 2.0 / borderRatio));
        // avoid rescaling if possible
        if (innerScale > 50 && innerScale < 70)
        {
            innerScale = 60;
        }
        borderStroke = new BasicStroke((int)Math
            .ceil((outerRect.width - innerScale) / 2.0));
        Point center = new Point(outerRect.x + outerRect.width / 2,
            outerRect.y + outerRect.height / 2);
        rect = new Rectangle(center.x - innerScale / 2, center.y - innerScale
            / 2, innerScale, innerScale);
        int midScale = (int)(Math.round((scale + innerScale) / 2.0));
        midRect = new Rectangle(center.x - midScale / 2, center.y - midScale
            / 2, midScale, midScale);
    }

    public String getDescription()
    {
        return getCreatureType().getName() + " in "
            + getCurrentHex().getLabel();
    }

    @Override
    public String toString()
    {
        return getDescription();
    }

    protected static void setUseColoredBorders(boolean bval)
    {
        useColoredBorders = bval;
    }

    public void setStrikeNumber(int strikeNumber)
    {
        this.strikeNumber = strikeNumber;
        if (strikeNumber > 0)
        {
            int fifthChitSize = (rect.height > rect.width ? (rect.width - 8) / 5
                : (rect.height - 8) / 5);
            strikeDie = new StrikeDie(fifthChitSize * 2, strikeNumber, "Hit");
            strikeDie.setToolTipText("Test");
            this.add(strikeDie);
            strikeAdjDie = new StrikeDie(fifthChitSize * 2, strikeNumber,
                "RedBlue");
        }
        else
        {
            strikeDie = null;
            strikeAdjDie = null;
        }
    }

    public void setStrikeDice(int numDice)
    {
        this.numDice = numDice;
    }

}
