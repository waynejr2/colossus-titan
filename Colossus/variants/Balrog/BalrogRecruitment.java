package Balrog;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import net.sf.colossus.client.PlayerClientSide;
import net.sf.colossus.game.Player;
import net.sf.colossus.server.CustomRecruitBase;
import net.sf.colossus.server.VariantSupport;
import net.sf.colossus.variant.CreatureType;
import net.sf.colossus.variant.MasterHex;


/**
 * Custom class to allow recruitment of Balrog in 6-tower game.
 * @version $Id$
 * @author Romain Dolbeau
 */
public class BalrogRecruitment extends CustomRecruitBase
{
    private static final Logger LOGGER = Logger
        .getLogger(BalrogRecruitment.class.getName());

    /**
     * Maps a player to their previous points count.
     */
    private final Map<Player, Integer> playerToOldScore = Collections
        .synchronizedMap(new HashMap<Player, Integer>());

    private final static int balrogValue = 300;
    private final static String balrogPrefix = "Balrog";

    @Override
    public List<CreatureType> getAllPossibleSpecialRecruiters(String terrain)
    {
        // Balrog recruited in Tower, where everything recruit anyway.
        return new ArrayList<CreatureType>();
    }

    @Override
    public List<CreatureType> getAllPossibleSpecialRecruits(String terrain)
    {
        List<CreatureType> temp = new ArrayList<CreatureType>();
        Iterator<CreatureType> it = VariantSupport.getCurrentVariant()
            .getCreatureTypes().iterator();
        while (it.hasNext())
        {
            CreatureType cre = it.next();
            if (cre.getName().startsWith(balrogPrefix))
            {
                temp.add(cre);
            }
        }
        return temp;
    }

    @Override
    public List<CreatureType> getPossibleSpecialRecruiters(String terrain,
        MasterHex hex)
    {
        // Balrog recruited in Tower, where everything recruit anyway.
        return new ArrayList<CreatureType>();
    }

    @Override
    public List<CreatureType> getPossibleSpecialRecruits(String terrain,
        MasterHex hex)
    {
        List<CreatureType> temp = new ArrayList<CreatureType>();

        if (hex == null)
        {
            return temp;
        }

        // need to update, as we might have earned points in the Engagement
        // phase and recruit in the Recruit phase
        updateBalrogCount(hex);

        String name = balrogPrefix + hex.getLabel();
        CreatureType balrogType = VariantSupport.getCurrentVariant()
            .getCreatureByName(name);

        if (getCount(balrogType) > 0)
        {
            temp.add(balrogType);
        }
        return temp;
    }

    @Override
    public int numberOfRecruiterNeeded(CreatureType recruiter,
        CreatureType recruit, String terrain, MasterHex hex)
    {
        return 0;
    }

    @Override
    protected void changeOfTurn(int newActivePlayer)
    {
        Set<MasterHex> towerSet = VariantSupport.getCurrentVariant()
            .getMasterBoard().getTowerSet();

        // update all Balrogs, as a lost fight may have given points
        // to a different Player
        for (MasterHex tower : towerSet)
        {
            updateBalrogCount(tower);
        }
    }

    private synchronized void updateBalrogCount(MasterHex tower)
    {
        String name = balrogPrefix + tower;

        Player pi = findPlayerWithStartingTower(tower);

        if (pi == null)
        {
            LOGGER.finest("CUSTOM: no player info for hex " + tower);
            return;
        }

        int oldscore;
        int newscore;
        int alreadyNumber;
        int nowNumber;

        synchronized (playerToOldScore)
        {
            Integer score = playerToOldScore.remove(pi);

            if (score == null)
            {
                oldscore = 0;
            }
            else
            {
                oldscore = score.intValue();
            }
            newscore = ((PlayerClientSide)pi).getScore();

            playerToOldScore.put(pi, Integer.valueOf(newscore));

            alreadyNumber = (oldscore / balrogValue);
            nowNumber = (newscore / balrogValue);
        }

        if (!VariantSupport.getCurrentVariant().isCreature(name))
        {
            LOGGER.severe("CUSTOM: Balrog by the name of " + name
                + " doesn't exist !");
            return;
        }

        CreatureType cre = VariantSupport.getCurrentVariant()
            .getCreatureByName(name);
        ((CreatureBalrog)cre).setNewMaxCount(nowNumber);

        int difference = nowNumber - alreadyNumber;
        int newcount = getCount(cre) + difference;

        setCount(cre, newcount);

        if (difference > 0)
        {
            LOGGER.finest("CUSTOM: Pushing the total number of " + name
                + " from " + alreadyNumber + " to " + nowNumber
                + " (new available count is: " + newcount + ")");
        }
        else if (difference < 0)
        {
            LOGGER.finest("CUSTOM: WARNING: DIMINISHING the total number of "
                + name + " from " + alreadyNumber + " to " + nowNumber
                + " (new available count is: " + newcount + ")");
        }
    }

    private Player findPlayerWithStartingTower(MasterHex tower)
    {
        for (Player player : allPlayers)
        {
            if (player.getStartingTower().equals(tower))
            {
                return player;
            }
        }
        return null;
    }

    @Override
    protected void resetInstance()
    {
        LOGGER.finest("CUSTOM: resetting " + getClass().getName());
        playerToOldScore.clear();
    }
}
