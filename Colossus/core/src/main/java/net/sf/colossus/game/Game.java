package net.sf.colossus.game;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import net.sf.colossus.server.VariantSupport;
import net.sf.colossus.variant.MasterBoardTerrain;
import net.sf.colossus.variant.MasterHex;
import net.sf.colossus.variant.Variant;
import net.sf.colossus.xmlparser.TerrainRecruitLoader;


/**
 * An ongoing game in Colossus.
 * 
 * As opposed to {@link Variant} this class holds information about an ongoing game
 * and its status.
 */
public class Game
{
    private static final Logger LOGGER = Logger
        .getLogger(Game.class.getName());
    /**
     * The variant played in this game.
     */
    private final Variant variant;

    /**
     * The state of the different players in the game. 
     * 
     * TODO use List instead
     */
    private final Player[] players;

    /**
     * The caretaker takes care of managing the available and dead creatures.
     */
    private final Caretaker caretaker;

    /** Last movement roll for any player. */
    private int movementRoll = -1;

    /**
     *  If a battle is ongoing, the masterBoard hex, attacker and defender
     */
    private Legion attacker;
    private Legion defender;
    private MasterHex battleSite;

    public Game(Variant variant, String[] playerNames)
    {
        this.variant = variant;
        this.players = new Player[playerNames.length];
        for (int i = 0; i < playerNames.length; i++)
        {
            players[i] = new Player(this, playerNames[i], i);
        }
        this.caretaker = new Caretaker(this);
    }

    public Variant getVariant()
    {
        if (variant != null)
        {
            return variant;
        }
        else
        {
            // TODO this is just temporarily until the variant member always gets initialized
            // properly
            return VariantSupport.getCurrentVariant();
        }
    }

    public int getMovementRoll()
    {
        return movementRoll;
    }

    public void setMovementRoll(int roll)
    {
        movementRoll = roll;
    }

    public void setEngagementData(MasterHex hex, Legion attacker,
        Legion defender)
    {
        this.battleSite = hex;
        this.attacker = attacker;
        this.defender = defender;
    }

    public MasterHex getBattleSite()
    {
        return battleSite;
    }

    public Legion getDefender()
    {
        return defender;
    }

    public Legion getAttacker()
    {
        return attacker;
    }

    /** 
     * Return a list of names of angel types that can be acquired based
     * on the hex in which legion is, when reaching given score threshold,
     * and if they are still available from caretaker
     * @param terrain The terrain in which this legion wants to acquire
     * @param score A acquring threshold, e.g. in Default 100, ..., 400, 500
     * @return list of acquirable names
     */
    List<String> findAvailableEligibleAngels(MasterBoardTerrain terrain,
        int score)
    {
        List<String> recruits = new ArrayList<String>();
        List<String> allRecruits = TerrainRecruitLoader
            .getRecruitableAcquirableList(terrain, score);
        Iterator<String> it = allRecruits.iterator();
        while (it.hasNext())
        {
            String name = it.next();

            if (getCaretaker().getAvailableCount(
                getVariant().getCreatureByName(name)) >= 1
                && !recruits.contains(name))
            {
                recruits.add(name);
            }
        }
        return recruits;
    }

    public Caretaker getCaretaker()
    {
        return caretaker;
    }

    // For making Proposals needed both client and server side
    public Legion getLegionByMarkerId(String markerId)
    {
        LOGGER.severe("getLegionByMarkerId called for markerId " + markerId
            + "in the non-overriden method of game.Game class!!");
        Thread.dumpStack();
        return null;
    }

}
