package net.sf.colossus.server;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.colossus.game.Creature;
import net.sf.colossus.game.EntrySide;
import net.sf.colossus.game.Legion;
import net.sf.colossus.game.Player;
import net.sf.colossus.game.events.AddCreatureEvent;
import net.sf.colossus.util.Glob;
import net.sf.colossus.variant.CreatureType;
import net.sf.colossus.variant.MasterHex;

import org.jdom.Element;


/**
 * Stores game history as XML.
 *
 * @author David Ripton
 */
public class History
{
    private static final Logger LOGGER = Logger.getLogger(History.class
        .getName());

    /**
     * History: events that happened before last commit point
     */
    private final Element root;

    /**
     * History elements/events that happened since the last commit/"snapshot".
     */
    private final List<Element> recentEvents = new LinkedList<Element>();

    /**
     * Set to true during the processing of {@link #fireEventsFromXML(Server)}
     * to avoid triggering events we just restored again.
     */
    private boolean loading = false;

    /**
     *
     */
    private final Element loadedRedoLog;

    /**
     * Stores the surviving legions (this variable is not needed any more)
     *
     * While the history should contain all information to reproduce the game
     * state, the last set of legions is currently still loaded upfront since
     * they contain the battle-specific information. This collides with
     * replaying the game from history...
     * Now, since 08/2008, they are not stored as "survivorlegions" any more.
     * Instead, they are backed up internally (done inside PlayerServerSide),
     * all the history is replayed. This creates proper split prediction data
     * in all clients. After that, backup data is compared with result of
     * replay.
     * E.g. Legion count, their content, players eliminated must be in sync.
     * Then the replayed ones are discarded and the backedup ones restored
     * - which have the right legion state (moved, donor, summoned, ...)
     *
     * TODO align the history replay more with the original gameplay so we
     *      don't need this anymore;
     *      08/2008:==> this is now to some part done. Still replay
     *      events could be closer to original events (split, summon,
     *      acquire, teleport, ...) , not just the "result" of that
     *      event (reveal,add,remove effects).
     *
     * TODO instead: model the actual events instead of just result,
     * or at least add relevant info to history elements, so that all
     * replayed events carry all needed data so that they could also be
     * processed by event viewer (currently EV does not process anything
     * during replay).
     */

    public History()
    {
        root = new Element("History");
        // Dummy:
        loadedRedoLog = new Element("LoadedRedoLog");
    }

    /**
     * Constructor used by "LoadGame"
     */
    public History(Element loadGameRoot)
    {
        // Get the history elements and store them to "root"
        root = (Element)loadGameRoot.getChild("History").clone();

        // Get the redo log content
        loadedRedoLog = (Element)loadGameRoot.getChild("Redo").clone();
    }

    /**
     *  All events before last commit
     */
    Element getCopy()
    {
        return (Element)root.clone();
    }

    /**
     * Reached a commit point: append all recent events to the history,
     * clear list of recent events; caller should do this together with creating
     * the next snapshot.
     */
    void flushRecentToRoot()
    {
        for (Element el : recentEvents)
        {
            // XXX
            // System.out.println("flushing element from redo to history: "
            //     + el.getName());
            el.detach();
            String name = el.getName();
            // TODO later, when this are proper events (not XML elements),
            // ask rather from the Event whether it belongs copied to
            // history or not.
            if (name.equals("LegionMoved") || name.equals("LegionMoved"))
            {
                LOGGER.finest("Flush Redo to History: skipping " + name);
            }
            else
            {
                root.addContent(el);
            }
        }
        recentEvents.clear();
    }

    /**
     *  @return A Redo Element, containing all events since last commit
     *  i.e. which need to be REDOne on top of last commit point/snapshot
     */
    Element getNewRedoLogElement()
    {
        Element redoLogElement = new Element("Redo");
        for (Element el : recentEvents)
        {
            // System.out
            //     .println("    adding recent event to new RedoLog, element "
            //         + el.getName());
            el.detach();
            redoLogElement.addContent(el);
        }

        return redoLogElement;
    }

    /**
     * TODO reconsider name
     * TODO decide if we should move it all into one big handleEvent(GameEvent) method
     */
    void addCreatureEvent(AddCreatureEvent event, int turn)
    {
        if (loading)
        {
            return;
        }
        Element element = new Element("AddCreature");
        element.setAttribute("markerId", event.getLegion().getMarkerId());
        element
            .setAttribute("creatureName", event.getAddedCreatureType().getName());
        element.setAttribute("turn", "" + turn);
        recentEvents.add(element);
    }

    void removeCreatureEvent(Legion legion, CreatureType creature, int turn)
    {
        if (loading)
        {
            return;
        }
        Element event = new Element("RemoveCreature");
        event.setAttribute("markerId", legion.getMarkerId());
        event.setAttribute("creatureName", creature.getName());
        event.setAttribute("turn", "" + turn);
        recentEvents.add(event);
    }

    void splitEvent(Legion parent, Legion child, List<CreatureType> splitoffs,
        int turn)
    {
        if (loading)
        {
            return;
        }
        Element event = new Element("Split");
        event.setAttribute("parentId", parent.getMarkerId());
        event.setAttribute("childId", child.getMarkerId());
        event.setAttribute("turn", "" + turn);
        Element creatures = new Element("splitoffs");
        event.addContent(creatures);
        for (CreatureType creatureType : splitoffs)
        {
            Element cr = new Element("creature");
            cr.addContent(creatureType.getName());
            creatures.addContent(cr);
        }
        recentEvents.add(event);
    }

    void mergeEvent(String splitoffId, String survivorId, int turn)
    {
        if (loading)
        {
            return;
        }
        Element event = new Element("Merge");
        event.setAttribute("splitoffId", splitoffId);
        event.setAttribute("survivorId", survivorId);
        event.setAttribute("turn", "" + turn);
        recentEvents.add(event);
    }

    void revealEvent(boolean allPlayers, List<Player> players,
        Legion legion, List<CreatureType> creatures, int turn)
    {
        if (loading)
        {
            return;
        }
        if (creatures.isEmpty())
        {
            // this happens e.g. when in final battle (titan vs. titan)
            // angel was called out of legion which was then empty,
            // and in the final updateAllLegionContents there is then
            // this empty legion...
            // TODO if this case can happen in a regular game no warning
            // should be logged
            LOGGER.log(Level.WARNING, "Called revealEvent(" + allPlayers
                + ", "
                + (players != null ? players.toString() : "-null-")
                + ", " + legion + ", " + creatures.toString() + ", "
                + turn + ") with empty creatureNames");
            return;
        }
        Element event = new Element("Reveal");
        event.setAttribute("markerId", legion.getMarkerId());
        event.setAttribute("allPlayers", "" + allPlayers);
        event.setAttribute("turn", "" + turn);
        if (!allPlayers)
        {
            Element viewers = new Element("viewers");
            event.addContent(viewers);
            Iterator<Player> it = players.iterator();
            while (it.hasNext())
            {
                String playerName = it.next().getName();
                Element viewer = new Element("viewer");
                viewer.addContent(playerName);
                viewers.addContent(viewer);
            }
        }
        Element creaturesElem = new Element("creatures");
        event.addContent(creaturesElem);
        for (CreatureType creatureType : creatures)
        {
            Element creatureElem = new Element("creature");
            creatureElem.addContent(creatureType.getName());
            creaturesElem.addContent(creatureElem);
        }
        recentEvents.add(event);
    }

    void playerElimEvent(Player player, Player slayer, int turn)
    {
        if (loading)
        {
            return;
        }
        Element event = new Element("PlayerElim");
        event.setAttribute("name", player.getName());
        if (slayer != null)
        {
            event.setAttribute("slayer", slayer.getName());
        }
        event.setAttribute("turn", "" + turn);
        recentEvents.add(event);
    }

    void movementRollEvent(Player player, int roll)
    {
        if (loading)
        {
            return;
        }

        Element event = new Element("MovementRoll");
        event.setAttribute("playerName", player.getName());
        event.setAttribute("roll", "" + roll);
        // System.out.println("created redo element movementroll");
        recentEvents.add(event);
    }

    void legionMovedEvent(Legion legion, MasterHex newHex,
        EntrySide entrySide,
        boolean teleport, CreatureType lord)
    {
        if (loading)
        {
            return;
        }

        Element event = new Element("LegionMoved");
        event.setAttribute("markerId", legion.getMarkerId());
        event.setAttribute("newHex", newHex.getLabel());
        event.setAttribute("entrySide", entrySide.getLabel());
        event.setAttribute("teleport", "" + teleport);
        String creNameOrTextNull = lord == null ? "null" : lord.getName();
        event.setAttribute("revealedLord", creNameOrTextNull);
        recentEvents.add(event);
    }

    /**
     * Now fire all events from redoLog. Note that "loading" is not set
     * to true, so they DO GET ADDED to the recentEvents list again.
     * @param server
     */
    void processRedoLog(Server server)
    {
        assert loadedRedoLog != null : "Loaded RedoLog should always "
            + "have a JDOM root element as backing store";

        LOGGER.info("Histroy: Start processing redo log");
        for (Object obj : loadedRedoLog.getChildren())
        {
            Element el = (Element)obj;
            // XXX
            // System.out.println("processing redo event " + el.getName());
            LOGGER.info("processing redo event " + el.getName());
            fireEventFromElement(server, el);
        }
        // TODO clear loadedRedoLog?
        LOGGER.info("Completed processing redo log");
    }

    // unchecked conversions from JDOM
    @SuppressWarnings("unchecked")
    void fireEventsFromXML(Server server)
    {
        this.loading = true;
        assert root != null : "History should always have a "
            + " JDOM root element as backing store";

        List<Element> kids = root.getChildren();
        Iterator<Element> it = kids.iterator();
        while (it.hasNext())
        {
            Element el = it.next();
            fireEventFromElement(server, el);
        }
        this.loading = false;
    }

    // unchecked conversions from JDOM
    @SuppressWarnings("unchecked")
    void fireEventFromElement(Server server, Element el)
    {
        GameServerSide game = server.getGame();
        if (el.getName().equals("Reveal"))
        {
            String allPlayers = el.getAttributeValue("allPlayers");
            boolean all = allPlayers != null && allPlayers.equals("true");
            String markerId = el.getAttributeValue("markerId");
            List<String> playerNames = new ArrayList<String>();
            Element viewEl = el.getChild("viewers");
            int turn = Integer.parseInt(el.getAttributeValue("turn"));
            String playerName = null;
            if (viewEl != null)
            {
                List<Element> viewers = viewEl.getChildren();
                Iterator<Element> it = viewers.iterator();
                while (it.hasNext())
                {
                    Element viewer = it.next();
                    playerName = viewer.getTextNormalize();
                    playerNames.add(playerName);
                }
            }
            List<Element> creatureElements = el.getChild("creatures")
                .getChildren();
            List<CreatureType> creatures = new ArrayList<CreatureType>();
            for (Element creature : creatureElements)
            {
                String creatureName = creature.getTextNormalize();
                creatures.add(game.getVariant()
                    .getCreatureByName(creatureName));
            }
            Player player = game.getPlayerByMarkerId(markerId);
            Legion legion;

            if (turn == 1 && player.getLegionByMarkerId(markerId) == null)
            {
                // there is no create event for the startup legions,
                // so we might need to create them for the reveal event
                legion = new LegionServerSide(markerId, null, player
                    .getStartingTower(), player.getStartingTower(), player,
                    game, creatures
                        .toArray(new CreatureType[creatures.size()]));
                player.addLegion(legion);
            }
            else
            {
                legion = player.getLegionByMarkerId(markerId);
            }
            String reason = "<unknown>";
            if (((PlayerServerSide)player).getDeadBeforeSave())
            {
                // Skip for players that will be dead by end of replay
            }
            else if (all)
            {
                server.allRevealCreatures(legion, creatures, reason);
            }
            else
            {
                server.oneRevealLegion(game.getPlayerByName(playerName), legion,
                    creatures, reason);
            }
        }
        else if (el.getName().equals("Split"))
        {
            String parentId = el.getAttributeValue("parentId");
            String childId = el.getAttributeValue("childId");
            String turnString = el.getAttributeValue("turn");
            int turn = Integer.parseInt(turnString);
            List<String> creatureNames = new ArrayList<String>();
            List<CreatureType> creatures = new ArrayList<CreatureType>();
            List<Element> splitoffs = el.getChild("splitoffs").getChildren();
            Iterator<Element> it = splitoffs.iterator();
            while (it.hasNext())
            {
                Element creature = it.next();
                String creatureName = creature.getTextNormalize();
                creatureNames.add(creatureName);
                creatures.add(game.getVariant()
                    .getCreatureByName(creatureName));
            }
            LegionServerSide parentLegion = game.getLegionByMarkerId(parentId);
            // LegionServerSide.split(..) doesn't like us here since the parent
            // legion can't remove creatures (not there?) -- create child directly
            // instead
            PlayerServerSide player = parentLegion.getPlayer();
            LegionServerSide childLegion;
            if (player.hasLegion(childId))
            {
                childLegion = game.getLegionByMarkerId(childId);
                LOGGER.severe("During replay of history: child legion "
                    + childId + " should not " + "exist yet (turn=" + turn
                    + ")!!\n" + "Exists already with: "
                    + Glob.glob(",", childLegion.getCreatureTypes())
                    + " but " + "should now be created with creatures: "
                    + creatures);

                childLegion.remove();
            }

            childLegion = new LegionServerSide(childId, null, parentLegion
                .getCurrentHex(), parentLegion.getCurrentHex(), player, game,
                creatures.toArray(new CreatureType[creatures.size()]));

            player.addLegion(childLegion);

            for (CreatureType creature : creatures)
            {
                parentLegion.removeCreature(creature, false, false);
            }

            // Skip for players that will be dead by end of replay
            if (!player.getDeadBeforeSave())
            {
                server.allTellDidSplit(parentLegion, childLegion, turn, false);
            }
        }
        else if (el.getName().equals("Merge"))
        {
            String splitoffId = el.getAttributeValue("splitoffId");
            String survivorId = el.getAttributeValue("survivorId");
            String turnString = el.getAttributeValue("turn");
            int turn = Integer.parseInt(turnString);

            LegionServerSide splitoff = game.getLegionByMarkerId(splitoffId);
            LegionServerSide survivor = game.getLegionByMarkerId(survivorId);

            // Skip for players that will be dead by end of replay
            if (!survivor.getPlayer().getDeadBeforeSave())
            {
                server.undidSplit(splitoff, survivor, false, turn);
            }
            // Add them back to parent:
            while (splitoff.getHeight() > 0)
            {
                CreatureType type = splitoff.removeCreature(0, false, false);
                survivor.addCreature(type, false);
            }
            splitoff.remove(false, false);
        }
        else if (el.getName().equals("AddCreature"))
        {
            String markerId = el.getAttributeValue("markerId");
            String creatureName = el.getAttributeValue("creatureName");
            String reason = "<unknown>";
            LOGGER.finer("Adding creature '" + creatureName
                + "' to legion with markerId '" + markerId + "', reason '"
                + reason + "'");
            LegionServerSide legion = game.getLegionByMarkerId(markerId);
            CreatureType creatureType = game.getVariant().getCreatureByName(
                creatureName);
            legion.addCreature(creatureType, false);
            // Skip for players that will be dead by end of replay
            if (!legion.getPlayer().getDeadBeforeSave())
            {
                server.allTellAddCreature(
                    new AddCreatureEvent(legion,
                    creatureType), false);
            }
            LOGGER.finest("Legion '" + markerId + "' now contains "
                + legion.getCreatures());
        }
        else if (el.getName().equals("RemoveCreature"))
        {
            String markerId = el.getAttributeValue("markerId");
            String creatureName = el.getAttributeValue("creatureName");
            String reason = "<unknown>";
            LOGGER.finer("Removing creature '" + creatureName
                + "' from legion with markerId '" + markerId + "', reason '"
                + reason + "'");
            LegionServerSide legion = game.getLegionByMarkerId(markerId);
            if (legion == null)
            {
                LOGGER.warning("removeCreature " + creatureName
                    + " from legion " + markerId + ", legion is null");
                return;
            }
            else
            {
                List<? extends Creature> cres = legion.getCreatures();
                List<String> crenames = new ArrayList<String>();
                for (Creature c : cres)
                {
                    crenames.add(c.getName());
                }
            }

            // don't use disbandIfEmpty parameter since that'll fire another history event
            CreatureType removedCritter = legion.removeCreature(game.
                getVariant().getCreatureByName(creatureName), false, false);

            // Skip for players that will be dead by end of replay
            // Skip if removedCritter is null => removeCreature did not find it,
            // so there is something wrong with the save game. No use to bother
            // all the clients with it.
            if (removedCritter != null
                && !legion.getPlayer().getDeadBeforeSave())
            {
                server.allTellRemoveCreature(legion, removedCritter, false,
                    reason);
            }
            LOGGER.finest("Legion '" + markerId + "' now contains "
                + legion.getCreatures());
            if (legion.getHeight() == 0)
            {
                legion.remove(false, false);
                LOGGER.finer("Legion '" + markerId + "' removed");
            }
        }
        else if (el.getName().equals("PlayerElim"))
        {
            String playerName = el.getAttributeValue("name");
            String slayerName = el.getAttributeValue("slayer");
            Player player = game.getPlayerByName(playerName);
            Player slayer = game.getPlayerByNameIgnoreNull(slayerName);
            // Record the slayer and give him this player's legion markers.
            if (slayer != null)
            {
                ((PlayerServerSide)player).handleSlaying(slayer);
            }
            player.setDead(true);
            server.allUpdatePlayerInfo();
            server.allTellPlayerElim(player, slayer, false);
        }

        else if (el.getName().equals("LegionMoved"))
        {
            // XXX XXX only moved ignored, movement roll taken into use try...
            // XXX Those two are right now not in use yet, because they will
            // cause errors because activePlayer is wrong.
            // Need to get "setupPhase" for ReDo done right first.
            // TODO also, for LegionMoved event, also need MoveUndone first,
            // otherwise problems if someone undoes a move before saving.
            return;
        }

        else if (el.getName().equals("MovementRoll"))
        {
            String playerName = el.getAttributeValue("playerName");
            Player player = game.getPlayerByName(playerName);
            int roll = Integer.parseInt(el.getAttributeValue("roll"));

            ((PlayerServerSide)player).setMovementRoll(roll);
            server.allTellMovementRoll(roll);
        }

        else if (el.getName().equals("LegionMoved"))
        {
            String markerId = el.getAttributeValue("markerId");
            String lordName = el.getAttributeValue("revealedLord");
            String tele = el.getAttributeValue("teleport");
            String newHexLabel = el.getAttributeValue("newHex");
            String entrySideName = el.getAttributeValue("entrySide");

            LegionServerSide legion = game.getLegionByMarkerId(markerId);
            CreatureType revealedLord = game.getVariant().getCreatureByName(
                lordName);
            MasterHex newHex = server.getGame().getVariant().getMasterBoard()
                .getHexByLabel(newHexLabel);
            EntrySide entrySide = EntrySide.fromLabel(entrySideName);
            boolean teleport = tele != null && tele.equals("true");

            LOGGER.finest("LegionMoved redo event: \n" + "  marker "
                + markerId + ", lordName " + revealedLord + " teleported "
                + teleport + " to hex " + newHex.getLabel() + " entrySide "
                + entrySide.toString());

            server.doMove(legion, newHex, entrySide, teleport, revealedLord);
        }
    }

}
