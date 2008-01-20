package net.sf.colossus.client;


import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.colossus.game.Player;
import net.sf.colossus.server.Constants;
import net.sf.colossus.util.Options;
import net.sf.colossus.util.Split;
import net.sf.colossus.variant.MasterHex;


/**
 * Class Movement handles client-side masterboard moves.
 * @version $Id$
 * @author David Ripton
 */

// XXX There is massive duplication of code between this class and
// the server-side movement code in Game.  Need to completely refactor.
public final class Movement
{
    private static final Logger LOGGER = Logger.getLogger(Movement.class
        .getName());

    Client client;

    Movement(Client client)
    {
        this.client = client;
    }

    /** Set the entry side relative to the hex label. */
    private static int findEntrySide(MasterHex hex, int cameFrom)
    {
        int entrySide = -1;
        if (cameFrom != -1)
        {
            if (HexMap.terrainHasStartlist(hex.getTerrain()))
            {
                entrySide = 3;
            }
            else
            {
                entrySide = (6 + cameFrom - hex.getLabelSide()) % 6;
            }
        }
        return entrySide;
    }

    /** Recursively find conventional moves from this hex.  
     *  If block >= 0, go only that way.  If block == -1, use arches and 
     *  arrows.  If block == -2, use only arrows.  Do not double back in
     *  the direction you just came from.  Return a set of 
     *  hexLabel:entrySide tuples. */
    private Set<String> findNormalMoves(MasterHex hex, LegionClientSide legion,
        int roll, int block, int cameFrom, String fromHexLabel)
    {
        Set<String> set = new HashSet<String>();
        String hexLabel = hex.getLabel();
        Player player = legion.getPlayer();

        // If there are enemy legions in this hex, mark it
        // as a legal move and stop recursing.  If there is
        // also a friendly legion there, just stop recursing.
        // Do a check versus fromHexLabel if we are evaluating
        // passing through this hex
        if (client.getNumEnemyLegions(hexLabel, player) > 0
            && !hexLabel.equals(fromHexLabel))
        {
            if (client.getNumFriendlyLegions(hexLabel, player) == 0)
            {
                // Set the entry side relative to the hex label.
                if (cameFrom != -1)
                {
                    set.add(hexLabel
                        + ":"
                        + BattleMap
                            .entrySideName(findEntrySide(hex, cameFrom)));
                }
            }
            return set;
        }

        if (roll == 0)
        {
            // This hex is the final destination.  Mark it as legal if
            // it is unoccupied by friendly legions that have already moved.
            // Account for spin cycles.
            if (client.getNumFriendlyLegions(hexLabel, player) > 0)
            {
                List<String> markerIds = client.getLegionsByHex(hexLabel);
                String markerId = markerIds.get(0);
                LegionClientSide hex_legion = client.getLegion(markerId);
                if (hex_legion.hasMoved())
                {
                    return set;
                }
            }

            if (cameFrom != -1)
            {
                set.add(hexLabel + ":"
                    + BattleMap.entrySideName(findEntrySide(hex, cameFrom)));
                return set;
            }
        }
        else if (roll < 0)
        {
            LOGGER.log(Level.SEVERE, "Movement.findNormalMoves() roll < 0");
            return null;
        }

        if (block >= 0)
        {
            set.addAll(findNormalMoves(hex.getNeighbor(block), legion,
                roll - 1, Constants.ARROWS_ONLY, (block + 3) % 6, null));
        }
        else if (block == Constants.ARCHES_AND_ARROWS)
        {
            for (int i = 0; i < 6; i++)
            {
                if (hex.getExitType(i) >= Constants.ARCH && i != cameFrom)
                {
                    set.addAll(findNormalMoves(hex.getNeighbor(i), legion,
                        roll - 1, Constants.ARROWS_ONLY, (i + 3) % 6, null));

                }
            }
        }
        else if (block == Constants.ARROWS_ONLY)
        {
            for (int i = 0; i < 6; i++)
            {
                if (hex.getExitType(i) >= Constants.ARROW && i != cameFrom)
                {
                    set.addAll(findNormalMoves(hex.getNeighbor(i), legion,
                        roll - 1, Constants.ARROWS_ONLY, (i + 3) % 6, null));
                }
            }
        }

        return set;
    }

    /** Recursively find all unoccupied hexes within roll hexes, for
     *  tower teleport. */
    private Set<String> findNearbyUnoccupiedHexes(MasterHex hex,
        LegionClientSide legion, int roll, int cameFrom)
    {
        // This hex is the final destination.  Mark it as legal if
        // it is unoccupied.
        String hexLabel = hex.getLabel();
        Set<String> set = new HashSet<String>();

        if (!client.isOccupied(hexLabel))
        {
            set.add(hexLabel);
        }

        if (roll > 0)
        {
            for (int i = 0; i < 6; i++)
            {
                if (i != cameFrom
                    && (hex.getExitType(i) != Constants.NONE || hex
                        .getEntranceType(i) != Constants.NONE))
                {
                    set.addAll(findNearbyUnoccupiedHexes(hex.getNeighbor(i),
                        legion, roll - 1, (i + 3) % 6));
                }
            }
        }

        return set;
    }

    /** Return set of hexLabels describing where this legion can move. */
    public Set<String> listAllMoves(LegionClientSide legion, MasterHex hex,
        int movementRoll)
    {
        return listAllMoves(legion, hex, movementRoll, false);
    }

    /** Return set of hexLabels describing where this legion can move. */
    public Set<String> listAllMoves(LegionClientSide legion, MasterHex hex,
        int movementRoll, boolean inAdvance)
    {
        Set<String> set = listNormalMoves(legion, hex, movementRoll,
            inAdvance, null);
        set.addAll(listTeleportMoves(legion, hex, movementRoll, inAdvance));
        return set;
    }

    private static int findBlock(MasterHex hex)
    {
        int block = Constants.ARCHES_AND_ARROWS;
        for (int j = 0; j < 6; j++)
        {
            if (hex.getExitType(j) == Constants.BLOCK)
            {
                // Only this path is allowed.
                block = j;
            }
        }
        return block;
    }

    /** Return set of hexLabels describing where this legion can move
     *  without teleporting. */
    public Set<String> listNormalMoves(LegionClientSide legion, MasterHex hex,
        int movementRoll, String fromHexLabel)
    {
        return listNormalMoves(legion, hex, movementRoll, false, fromHexLabel);
    }

    public Set<String> listNormalMoves(LegionClientSide legion, MasterHex hex,
        int movementRoll)
    {
        return listNormalMoves(legion, hex, movementRoll, false, null);
    }

    /** Return set of hexLabels describing where this legion can move
     *  without teleporting. */
    public Set<String> listNormalMoves(LegionClientSide legion, MasterHex hex,
        int movementRoll, boolean inAdvance, String fromHexLabel)
    {
        if (hex == null || (legion.hasMoved() && (!inAdvance)))
        {
            return new HashSet<String>();
        }

        Set<String> tuples = findNormalMoves(hex, legion, movementRoll,
            findBlock(hex), Constants.NOWHERE, fromHexLabel);

        // Extract just the hexLabels from the hexLabel:entrySide tuples.
        Set<String> hexLabels = new HashSet<String>();
        Iterator<String> it = tuples.iterator();
        while (it.hasNext())
        {
            String tuple = it.next();
            List<String> parts = Split.split(':', tuple);
            String hexLabel = parts.get(0);
            hexLabels.add(hexLabel);
        }
        return hexLabels;
    }

    private boolean towerTeleportAllowed()
    {
        if (client.getOptions().getOption(Options.noTowerTeleport))
        {
            return false;
        }
        if (client.getTurnNumber() == 1
            && client.getOptions().getOption(Options.noFirstTurnTeleport))
        {
            return false;
        }
        return true;
    }

    private boolean towerToTowerTeleportAllowed()
    {
        if (!towerTeleportAllowed())
        {
            return false;
        }
        if (client.getTurnNumber() == 1
            && client.getOptions().getOption(Options.noFirstTurnT2TTeleport))
        {
            return false;
        }
        return true;
    }

    private boolean towerToNonTowerTeleportAllowed()
    {
        if (!towerTeleportAllowed())
        {
            return false;
        }
        if (client.getOptions().getOption(Options.towerToTowerTeleportOnly))
        {
            return false;
        }
        return true;
    }

    public boolean titanTeleportAllowed()
    {
        if (client.getOptions().getOption(Options.noTitanTeleport))
        {
            return false;
        }
        if (client.getTurnNumber() == 1
            && client.getOptions().getOption(Options.noFirstTurnTeleport))
        {
            return false;
        }
        return true;
    }

    /** Return set of hexLabels describing where this legion can teleport. */
    public Set<String> listTeleportMoves(LegionClientSide legion, MasterHex hex,
        int movementRoll)
    {
        return listTeleportMoves(legion, hex, movementRoll, false);
    }

    /** Return set of hexLabels describing where this legion can teleport. */
    Set<String> listTeleportMoves(LegionClientSide legion, MasterHex hex,
        int movementRoll, boolean inAdvance)
    {
        PlayerClientSide player = legion.getPlayer();

        Set<String> set = new HashSet<String>();
        if (hex == null
            || ((!inAdvance) && (movementRoll != 6 || legion.hasMoved() || player
                .hasTeleported())))
        {
            return set;
        }

        // Tower teleport
        if (HexMap.terrainIsTower(hex.getTerrain()) && legion.numLords() > 0
            && towerTeleportAllowed())
        {
            // Mark every unoccupied hex within 6 hexes.
            if (towerToNonTowerTeleportAllowed())
            {
                set.addAll(findNearbyUnoccupiedHexes(hex, legion, 6,
                    Constants.NOWHERE));
            }

            if (towerToTowerTeleportAllowed())
            {
                // Mark every unoccupied tower.
                Set<String> towerSet = client.getGame().getVariant()
                    .getMasterBoard().getTowerSet();
                Iterator<String> it = towerSet.iterator();
                while (it.hasNext())
                {
                    String hexLabel = it.next();
                    if (client.getGame().getVariant().getMasterBoard()
                        .getHexByLabel(hexLabel) != null)
                    {
                        if (!client.isOccupied(hexLabel)
                            && (!(hexLabel.equals(hex.getLabel()))))
                        {
                            set.add(hexLabel);
                        }
                    }
                }
            }
            else
            {
                // Remove nearby towers from set.
                Set<String> towerSet = client.getGame().getVariant()
                    .getMasterBoard().getTowerSet();
                Iterator<String> it = towerSet.iterator();
                while (it.hasNext())
                {
                    String hexLabel = it.next();
                    set.remove(hexLabel);
                }
            }
        }

        // Titan teleport
        if (player.canTitanTeleport() && legion.hasTitan()
            && titanTeleportAllowed())
        {
            // Mark every hex containing an enemy stack that does not
            // already contain a friendly stack.
            Iterator<String> it = client.getEnemyLegions(player).iterator();
            while (it.hasNext())
            {
                String markerId = it.next();
                LegionClientSide other = client.getLegion(markerId);
                {
                    String hexLabel = other.getHexLabel();
                    if (hexLabel != null && !client.isEngagement(hexLabel))
                    {
                        set.add(hexLabel);
                    }
                }
            }
        }
        set.remove(null);
        set.remove("null");
        return set;
    }

    /** Return a Set of Strings "Left" "Right" or "Bottom" describing
     *  possible entry sides.  If the hex is unoccupied, just return 
     *  one entry side since it doesn't matter. */
    Set<String> listPossibleEntrySides(String markerId, String targetHexLabel,
        boolean teleport)
    {
        Set<String> entrySides = new HashSet<String>();
        LegionClientSide legion = client.getLegion(markerId);
        int movementRoll = client.getMovementRoll();
        MasterHex currentHex = client.getGame().getVariant().getMasterBoard()
            .getHexByLabel(legion.getHexLabel());
        MasterHex targetHex = client.getGame().getVariant().getMasterBoard()
            .getHexByLabel(targetHexLabel);

        if (teleport)
        {
            if (listTeleportMoves(legion, currentHex, movementRoll).contains(
                targetHexLabel))
            {
                // Startlisted terrain only have bottom entry side.
                // Don't bother finding more than one entry side if unoccupied.
                if (!client.isOccupied(targetHexLabel)
                    || HexMap.terrainHasStartlist(targetHex.getTerrain()))

                {
                    entrySides.add(Constants.bottom);
                    return entrySides;
                }
                else
                {
                    entrySides.add(Constants.bottom);
                    entrySides.add(Constants.left);
                    entrySides.add(Constants.right);
                    return entrySides;
                }
            }
            else
            {
                return entrySides;
            }
        }

        // Normal moves.
        Set<String> tuples = findNormalMoves(currentHex, legion, movementRoll,
            findBlock(currentHex), Constants.NOWHERE, null);
        Iterator<String> it = tuples.iterator();
        while (it.hasNext())
        {
            String tuple = it.next();
            List<String> parts = Split.split(':', tuple);
            String hl = parts.get(0);
            if (hl.equals(targetHexLabel))
            {
                String buf = parts.get(1);
                entrySides.add(buf);
                // Don't bother finding more than one entry side if unoccupied.
                if (!client.isOccupied(targetHexLabel))
                {
                    return entrySides;
                }
            }
        }
        return entrySides;
    }

    public void dispose()
    {
        this.client = null;
    }
}
