package net.sf.colossus.server;


import java.util.*;
import java.net.*;
import javax.swing.*;
import java.io.*;

import net.sf.colossus.util.Log;
import net.sf.colossus.util.Options;
import net.sf.colossus.client.IClient;
import net.sf.colossus.client.Client;
import net.sf.colossus.client.Proposal;
import net.sf.colossus.parser.TerrainRecruitLoader;

/**
 *  Class Server lives on the server side and handles all communcation with
 *  the clients.  It talks to the server classes locally, and to the Clients
 *  via the network protocol.
 *  @version $Id$
 *  @author David Ripton
 */
public final class Server implements IServer
{
    private Game game;

    /** 
     *  Maybe also save things like the originating IP, in case a 
     *  connection breaks and we need to authenticate reconnects.  
     *  Do not share these references. */
    private List clients = new ArrayList();
    private List remoteClients = new ArrayList();

    /** Map of player name to client. */
    private Map clientMap = new HashMap();

    /** Number of remote clients we're waiting for. */
    private int waitingForClients;

    /** Server socket port. */
    private int port;

    // Cached strike information.
    private Critter striker;
    private Critter target;
    private int strikeNumber;
    private int damage;
    private List rolls;

    // Network stuff
    private ServerSocket serverSocket;
    // list of Socket that are currently active
    private java.util.List activeSocketList =
        Collections.synchronizedList(new ArrayList());
    private int numClients;
    private int maxClients;

    private static Thread fileServerThread = null; /* static so that new instance of Server can destroy a previously allocated FileServerThread */

    Server(Game game, int port)
    {
        this.game = game;
        this.port = port;
        waitingForClients = game.getNumLivingPlayers();
    }

    void initSocketServer()
    {
        numClients = 0;
        maxClients = game.getNumLivingPlayers();
        Log.debug("initSocketServer maxClients = " + maxClients);
        Log.debug("About to create server socket on port " + port);
        try
        {
            if (serverSocket != null)
            {
                serverSocket.close();
                serverSocket = null;
                // Bug 626646 -- bind exception here on NT 4
                // when restarting game
                // XXX Pausing for a few seconds may or may not help
                // TODO Set SO_REUSEADDR, when we require JDK 1.4
                try
                {
                    Thread.sleep(3000);
                }
                catch (InterruptedException ex)
                {
                    Log.error(ex.toString());
                }
            }
            serverSocket = new ServerSocket(port, Constants.MAX_MAX_PLAYERS);
        }
        catch (IOException ex)
        {
            Log.error("Could not create socket. Configure networking in OS.");
            Log.error(ex.toString());
            ex.printStackTrace();
            System.exit(1);
        }
        createLocalClients();

        while (numClients < maxClients)
        {
            waitForConnection();
        }
    }

    void initFileServer()
    {
        // must be called *after* initSocketServer()
        // this may induce a race condition
        // if the client ask for a file *before*
        // the file server is up.

        Log.debug("About to create file server socket on port " + (port + 1));

        if (fileServerThread != null)
        {
            try {
                Log.debug("Stopping the FileServerThread ");
                ((FileServerThread)fileServerThread).stopGoingOn();
                fileServerThread.interrupt();
                fileServerThread.join();
            }
            catch (Exception e)
            {
                Log.debug("Oups couldn't stop the FileServerThread " + e);
            }
            fileServerThread = null;
        }
        
        if (!activeSocketList.isEmpty())
        {
            fileServerThread = new FileServerThread(activeSocketList,
                                                    port + 1);
            fileServerThread.start();
        }
        else
        {
            Log.debug("No active remote client, not lauching the file server.");
        }
    }
    
    private void waitForConnection()
    {
        Socket clientSocket = null;
        try
        {
            clientSocket = serverSocket.accept();
            Log.event("Got client connection from " +
                clientSocket.getInetAddress().toString());
            synchronized (activeSocketList)
            {
                activeSocketList.add(clientSocket);
            }
            numClients++;
        }
        catch (IOException ex)
        {
            Log.error(ex.toString());
            ex.printStackTrace();
            return;
        }

        new SocketServerThread(this, clientSocket, activeSocketList).start();
    }


    /** Each server thread's name is set to its player's name. */
    String getPlayerName()
    {
        return Thread.currentThread().getName();
    }

    private Player getPlayer()
    {
        return game.getPlayer(getPlayerName());
    }

    private boolean isActivePlayer()
    {
        return getPlayerName().equals(game.getActivePlayerName());
    }

    private boolean isBattleActivePlayer()
    {
        return game.getBattle() != null &&
            game.getBattle().getActivePlayerName() != null &&
            getPlayerName().equals(game.getBattle().getActivePlayerName());
    }


    private void createLocalClients()
    {
        for (int i = 0; i < game.getNumPlayers(); i++)
        {
            Player player = game.getPlayer(i);
            if (!player.isDead() &&
                !player.getType().endsWith(Constants.network))
            {
                createLocalClient(player.getName());
            }
        }
    }

    private void createLocalClient(String playerName)
    {
        Log.debug("Called Server.createLocalClient() for " + playerName);
        IClient client = new Client("127.0.0.1", port, playerName, false);
    }


    synchronized void addClient(final IClient client, final String playerName,
        final boolean remote)
    {
        Log.debug("Called Server.addClient() for " + playerName);
        clients.add(client);

        if (remote)
        {
            addRemoteClient(client, playerName);
        }
        else
        {
            addLocalClient(client, playerName);
        }

        waitingForClients--;
        Log.event("Decremented waitingForClients to " + waitingForClients);
        if (waitingForClients <= 0)
        {
            if (game.isLoadingGame())
            {
                game.loadGame2();
            }
            else
            {
                game.newGame2();
            }
        }
    }

    private void addLocalClient(final IClient client, final String playerName)
    {
        clientMap.put(playerName, client);
    }

    private void addRemoteClient(final IClient client, final String playerName)
    {
        String name = playerName;
        int slot = game.findNetworkSlot(playerName);
        if (slot == -1)
        {
            return;
        }

        Log.setServer(this);
        Log.setToRemote(true);
        remoteClients.add(client);

        if (!game.isLoadingGame())
        {
            name = game.getUniqueName(playerName);
        }

        clientMap.put(name, client);
        Player player = game.getPlayer(slot);
        player.setName(name);
        // In case we had to change a duplicate name.
        setPlayerName(name, name);
    }


    void disposeAllClients()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.dispose();
        }
        clients.clear();
        if (serverSocket != null)
        {
            try
            {
                serverSocket.close();
            }
            catch (IOException ex)
            {
                Log.error(ex.toString());
            }
        }
    }


    void allUpdatePlayerInfo(boolean treatDeadAsAlive)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.updatePlayerInfo(getPlayerInfo(treatDeadAsAlive));
        }
    }

    void allUpdatePlayerInfo()
    {
        allUpdatePlayerInfo(false);
    }

    void allUpdateCreatureCount(String creatureName, int count, int deadCount)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.updateCreatureCount(creatureName, count, deadCount);
        }
    }


    void allTellMovementRoll(int roll)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.tellMovementRoll(roll);
        }
    }


    public void leaveCarryMode()
    {
        if (!isBattleActivePlayer())
        {
            Log.error(getPlayerName() + " illegally called leaveCarryMode()");
            return;
        }
        Battle battle = game.getBattle();
        battle.leaveCarryMode();
    }


    public void doneWithBattleMoves()
    {
        if (!isBattleActivePlayer())
        {
            Log.error(getPlayerName() + 
                " illegally called doneWithBattleMoves()");
            return;
        }
        Battle battle = game.getBattle();
        battle.doneWithMoves();
    }


    public void doneWithStrikes()
    {
        Battle battle = game.getBattle();
        if (!isBattleActivePlayer() || battle.getPhase() < Constants.FIGHT)
        {
            Log.error(getPlayerName() + " illegally called doneWithStrikes()");
            return;
        }
        if (!battle.doneWithStrikes())
        {
            showMessageDialog("Must take forced strikes");
        }
    }


    private IClient getClient(String playerName)
    {
        if (clientMap.containsKey(playerName))
        {
            return (IClient)clientMap.get(playerName);
        }
        else
        {
            return null;
        }
    }


    /** Return the name of the first human-controlled client, or null if
     *  all clients are AI-controlled. */
    private String getFirstHumanClientName()
    {
        for (int i = 0; i < game.getNumPlayers(); i++)
        {
            Player player = game.getPlayer(i);
            if (player.isHuman())
            {
                return player.getName();
            }
        }
        return null;
    }


    synchronized void allInitBoard()
    {
        Iterator it = game.getPlayers().iterator();
        while (it.hasNext())
        {
            Player player = (Player)it.next();
            if (!player.isDead())
            {
                IClient client = getClient(player.getName());
                if (client != null)
                {
                    client.initBoard();
                }
            }
        }
    }


    synchronized void allTellAllLegionLocations()
    {
        List markerIds = game.getAllLegionIds();
        Iterator it = markerIds.iterator();
        while (it.hasNext())
        {
            String markerId = (String)it.next();
            allTellLegionLocation(markerId);
        }
    }

    void allTellLegionLocation(String markerId)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        String hexLabel = legion.getCurrentHexLabel();

        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.tellLegionLocation(markerId, hexLabel);
        }
    }

    void allRemoveLegion(String markerId)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.removeLegion(markerId);
        }
    }


    private void showMessageDialog(final String message)
    {
        showMessageDialog(getPlayerName(), message);
    }

    void showMessageDialog(String playerName, String message)
    {
        IClient client = getClient(playerName);
        client.showMessageDialog(message);
    }

    void allShowMessageDialog(String message)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.showMessageDialog(message);
        }
    }

    void allTellPlayerElim(String playerName, String slayerName)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.tellPlayerElim(playerName, slayerName);
        }
        game.history.playerElimEvent(playerName, slayerName);
    }

    void allTellGameOver(String message)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.tellGameOver(message);
        }
    }


    /** Needed if loading game outside the split phase. */
    synchronized void allSetupTurnState()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.setupTurnState(game.getActivePlayerName(),
                game.getTurnNumber());
        }
    }

    void allSetupSplit()
    {
        Iterator it = game.getPlayers().iterator();
        while (it.hasNext())
        {
            Player player = (Player)it.next();
            IClient client = getClient(player.getName());
            if (client != null)
            {
                client.setupSplit(game.getActivePlayerName(), 
                    game.getTurnNumber());
            }
        }
        allUpdatePlayerInfo();
    }


    void allSetupMove()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.setupMove();
        }
    }

    void allSetupFight()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.setupFight();
        }
    }

    void allSetupMuster()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.setupMuster();
        }
    }


    void allSetupBattleSummon()
    {
        Battle battle = game.getBattle();
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.setupBattleSummon(battle.getActivePlayerName(),
                battle.getTurnNumber());
        }
    }

    void allSetupBattleRecruit()
    {
        Battle battle = game.getBattle();
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.setupBattleRecruit(battle.getActivePlayerName(),
                battle.getTurnNumber());
        }
    }

    void allSetupBattleMove()
    {
        Battle battle = game.getBattle();
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.setupBattleMove(battle.getActivePlayerName(),
                battle.getTurnNumber());
        }
    }

    void allSetupBattleFight()
    {
        Battle battle = game.getBattle();
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            if (battle != null)
            {
                client.setupBattleFight(battle.getPhase(),
                    battle.getActivePlayerName());
            }
        }
    }


    synchronized void allPlaceNewChit(Critter critter)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.placeNewChit(critter.getName(),
                critter.getMarkerId().equals(game.getBattle().getDefenderId()),
                critter.getTag(), critter.getCurrentHexLabel());
        }
    }


    void allRemoveDeadBattleChits()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.removeDeadBattleChits();
        }
    }


    void allHighlightEngagements()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.highlightEngagements();
        }
    }


    void allTellEngagementResults(String winnerId, String method, int points)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.tellEngagementResults(winnerId, method, points);
        }
    }

    void nextEngagement()
    {
        IClient client = getClient(game.getActivePlayerName());
        client.nextEngagement();
    }


    /** Find out if the player wants to acquire an angel or archangel. */
    synchronized void askAcquireAngel(String playerName, String markerId, 
        List recruits)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        if (legion.getHeight() < 7)
        {
            IClient client = getClient(playerName);
            if (client != null)
            {
                client.askAcquireAngel(markerId, recruits);
            }
        }
    }

    public void acquireAngel(String markerId, String angelType)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        if (!getPlayerName().equals(legion.getPlayerName()))
        {
            Log.error(getPlayerName() + " illegally called acquireAngel()");
            return;
        }

        if (legion != null)
        {
            legion.addAngel(angelType);
        }
    }


    void createSummonAngel(Legion legion)
    {
        IClient client = getClient(legion.getPlayerName());
        client.createSummonAngel(legion.getMarkerId());
    }

    void reinforce(Legion legion)
    {
        IClient client = getClient(legion.getPlayerName());
        client.doReinforce(legion.getMarkerId());
    }

    public void doSummon(String markerId, String donorId, String angel)
    {
        if (!isActivePlayer())
        {
            Log.error(getPlayerName() + " illegally called doSummon()");
            return;
        }
        Legion legion = game.getLegionByMarkerId(markerId);
        Legion donor = game.getLegionByMarkerId(donorId);
        Creature creature = null;
        if (angel != null)
        {
            creature = Creature.getCreatureByName(angel);
        }
        game.doSummon(legion, donor, creature);
    }


    /**
     * Handle mustering for legion.
     * if recruiting with nothing, recruiterName is a non-null String
     * that contains "null".
     */
    public void doRecruit(String markerId, String recruitName,
        String recruiterName)
    {
        IClient client = getClient(getPlayerName());

        Legion legion = game.getLegionByMarkerId(markerId);
        if (!getPlayerName().equals(legion.getPlayerName()))
        {
            Log.error(getPlayerName() + " illegally called doRecruit()");
            client.nakRecruit(markerId);
            return;
        }

        if (legion != null && (legion.hasMoved() || game.getPhase() ==
            Constants.FIGHT) && legion.canRecruit())
        {
            legion.sortCritters();
            Creature recruit = null;
            Creature recruiter = null;
            if (recruitName != null)
            {
                recruit = Creature.getCreatureByName(recruitName);
                recruiter = Creature.getCreatureByName(recruiterName);
                if (recruit != null)
                {
                    game.doRecruit(legion, recruit, recruiter);
                }
            }

            if (!legion.canRecruit())
            {
                didRecruit(legion, recruit, recruiter);
            }
        }
        else
        {
            client.nakRecruit(markerId);

            // XXX -- uncomment after verifying it doesn't cause hangs
            // during reinforcement phase.  (May need to handle
            // nak first.) 
            return;
        }

        // Need to always call this to keep game from hanging.
        if (game.getPhase() == Constants.FIGHT)
        {
            if (game.getBattle() != null)
            {
                game.getBattle().doneReinforcing();
            }
            else
            {
                game.doneReinforcing();
            }
        }
    }

    void didRecruit(Legion legion, Creature recruit, Creature recruiter)
    {
        allUpdatePlayerInfo();

        int numRecruiters = (recruiter == null ? 0 :
             TerrainRecruitLoader.numberOfRecruiterNeeded(
             recruiter, recruit, legion.getCurrentHex().getTerrain(),
             legion.getCurrentHex().getLabel()));
        String recruiterName = null;
        if (recruiter != null)
        {
            recruiterName = recruiter.getName();
        }

        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.didRecruit(legion.getMarkerId(), recruit.getName(),
                recruiterName, numRecruiters);
        }

        List recruiterNames = new ArrayList();
        for (int i = 0; i < numRecruiters; i++)
        {
            recruiterNames.add(recruiterName);
        }
        game.history.revealEvent(true, null, legion.getMarkerId(), 
            recruiterNames, false);
        game.history.addCreatureEvent(legion.getMarkerId(), recruit.getName());
    }

    void undidRecruit(Legion legion, String recruitName)
    {
        allUpdatePlayerInfo();
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.undidRecruit(legion.getMarkerId(), recruitName);
        }
        game.history.removeCreatureEvent(legion.getMarkerId(), recruitName);
    }


    public synchronized void engage(String hexLabel)
    {
        if (!isActivePlayer())
        {
            Log.error(getPlayerName() + " illegally called engage()");
            return;
        }
        game.engage(hexLabel);
    }

    void allTellEngagement(String hexLabel, Legion attacker, Legion defender)
    {
        Log.debug("allTellEngagement() " + hexLabel);
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.tellEngagement(hexLabel, attacker.getMarkerId(), 
                defender.getMarkerId());
        }
    }


    /** Ask ally's player whether he wants to concede with ally. */
    void askConcede(Legion ally, Legion enemy)
    {
        IClient client = getClient(ally.getPlayerName());
        client.askConcede(ally.getMarkerId(), enemy.getMarkerId());
    }

    public void concede(String markerId)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        if (!getPlayerName().equals(legion.getPlayerName()))
        {
            Log.error(getPlayerName() + " illegally called concede()");
            return;
        }
        game.concede(markerId);
    }

    public void doNotConcede(String markerId)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        if (!getPlayerName().equals(legion.getPlayerName()))
        {
            Log.error(getPlayerName() + " illegally called doNotConcede()");
            return;
        }
        game.doNotConcede(markerId);
    }

    /** Ask ally's player whether he wants to flee with ally. */
    void askFlee(Legion ally, Legion enemy)
    {
        IClient client = getClient(ally.getPlayerName());
        client.askFlee(ally.getMarkerId(), enemy.getMarkerId());
    }

    public void flee(String markerId)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        if (!getPlayerName().equals(legion.getPlayerName()))
        {
            Log.error(getPlayerName() + " illegally called flee()");
            return;
        }
        game.flee(markerId);
    }

    public void doNotFlee(String markerId)
    {
        Legion legion = game.getLegionByMarkerId(markerId);
        if (!getPlayerName().equals(legion.getPlayerName()))
        {
            Log.error(getPlayerName() + " illegally called doNotFlee()");
            return;
        }
        game.doNotFlee(markerId);
    }


    void twoNegotiate(Legion attacker, Legion defender)
    {
        IClient client1 = getClient(defender.getPlayerName());
        client1.askNegotiate(attacker.getMarkerId(), defender.getMarkerId());

        IClient client2 = getClient(attacker.getPlayerName());
        client2.askNegotiate(attacker.getMarkerId(), defender.getMarkerId());
    }

    /** playerName makes a proposal. */
    public void makeProposal(String proposalString)
    {
        // XXX Validate calling player
        game.makeProposal(getPlayerName(), proposalString);
    }

    /** Tell playerName about proposal. */
    void tellProposal(String playerName, Proposal proposal)
    {
        IClient client = getClient(playerName);
        client.tellProposal(proposal.toString());
    }

    public void fight(String hexLabel)
    {
        // XXX Validate calling player
        game.fight(hexLabel);
    }


    public void doBattleMove(int tag, String hexLabel)
    {
        IClient client = getClient(getPlayerName());
        if (!isBattleActivePlayer())
        {
            Log.error(getPlayerName() + " illegally called doBattleMove()");
            client.nakBattleMove(tag);
            return;
        }
        boolean moved = game.getBattle().doMove(tag, hexLabel);
        if (!moved)
        {
            client.nakBattleMove(tag);
        }
    }

    void allTellBattleMove(int tag, String startingHex, String endingHex, 
        boolean undo)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.tellBattleMove(tag, startingHex, endingHex, undo);
        }
    }


    public synchronized void strike(int tag, String hexLabel)
    {
        IClient client = getClient(getPlayerName());
        if (!isBattleActivePlayer())
        {
            Log.error(getPlayerName() + " illegally called strike()");
            client.nakStrike(tag);
            return;
        }
        Battle battle = game.getBattle();
        if (battle == null)
        {
            Log.error("null battle in Server.strike()");
            client.nakStrike(tag);
            return;
        }
        Legion legion = battle.getActiveLegion();
        if (legion == null)
        {
            Log.error("null active legion in Server.strike()");
            client.nakStrike(tag);
            return;
        }
        Critter critter = legion.getCritterByTag(tag);
        if (critter == null)
        {
            Log.error("No critter with tag " + tag + " in Server.strike()");
            // XXX Hang here.
            client.nakStrike(tag);
            return;
        }
        Critter target = battle.getCritter(hexLabel);
        if (target == null)
        {
            Log.error("No target in hex " + hexLabel + " in Server.strike()");
            client.nakStrike(tag);
            return;
        }
        critter.strike(target);
    }

    public synchronized void applyCarries(String hexLabel)
    {
        if (!isBattleActivePlayer())
        {
            Log.error(getPlayerName() + " illegally called applyCarries()");
            return;
        }
        Battle battle = game.getBattle();
        Critter target = battle.getCritter(hexLabel);
        battle.applyCarries(target);
    }


    public void undoBattleMove(String hexLabel)
    {
        if (!isBattleActivePlayer())
        {
            Log.error(getPlayerName() + " illegally called undoBattleMove()");
            return;
        }
        game.getBattle().undoMove(hexLabel);
    }


    synchronized void allTellStrikeResults(Critter striker, Critter target,
        int strikeNumber, List rolls, int damage, int carryDamageLeft,
        Set carryTargetDescriptions)
    {
        // Save strike info so that it can be reused for carries.
        this.striker = striker;
        this.target = target;
        this.strikeNumber = strikeNumber;
        this.damage = damage;
        this.rolls = rolls;

        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.tellStrikeResults(striker.getTag(), target.getTag(),
                strikeNumber, rolls, damage, target.isDead(), false,
                carryDamageLeft, carryTargetDescriptions);
        }
    }

    synchronized void allTellCarryResults(Critter carryTarget, 
        int carryDamageDone, int carryDamageLeft, Set carryTargetDescriptions)
    {
        if (striker == null || target == null || rolls == null)
        {
            Log.error("Called allTellCarryResults() without setup.");
            if (striker == null)
            {
                Log.error("null striker");
            }
            if (target == null)
            {
                Log.error("null target");
            }
            if (rolls == null)
            {
                Log.error("null rolls");
            }
            return;
        }
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.tellStrikeResults(striker.getTag(), carryTarget.getTag(), 
                strikeNumber, rolls, carryDamageDone, carryTarget.isDead(), 
                true, carryDamageLeft, carryTargetDescriptions);
        }
    }

    
    synchronized void allTellHexDamageResults(Critter target, int damage)
    {
        this.target = target;
        this.damage = damage;

        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.tellStrikeResults(Constants.hexDamage, target.getTag(), 
                0, null, damage, target.isDead(), false, 0, null);
        }
    }


    /** Takes a Set of PenaltyOptions. */
    void askChooseStrikePenalty(SortedSet penaltyOptions)
    {
        String playerName = game.getBattle().getActivePlayerName();
        IClient client = getClient(playerName);
        ArrayList choices = new ArrayList();
        Iterator it = penaltyOptions.iterator();
        while (it.hasNext())
        {
            PenaltyOption po = (PenaltyOption)it.next();
            striker = po.getStriker();
            choices.add(po.toString());
        }
        client.askChooseStrikePenalty(choices);
    }

    public void assignStrikePenalty(String prompt)
    {
        if (!isBattleActivePlayer())
        {
            Log.error(getPlayerName() + 
                " illegally called assignStrikePenalty()");
            return;
        }
        striker.assignStrikePenalty(prompt);
    }

    synchronized void allInitBattle(String masterHexLabel)
    {
        Battle battle = game.getBattle();
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.initBattle(masterHexLabel, battle.getTurnNumber(),
                battle.getActivePlayerName(), battle.getPhase(),
                battle.getAttackerId(), battle.getDefenderId());
        }
    }


    void allCleanupBattle()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.cleanupBattle();
        }
    }


    public void mulligan()
    {
        if (!isActivePlayer())
        {
            Log.error(getPlayerName() + " illegally called mulligan()");
            return;
        }
        int roll = game.mulligan();
        Log.event(getPlayerName() + " takes a mulligan and rolls " + roll);
        if (roll != -1)
        {
            allTellMovementRoll(roll);
        }
    }


    public void undoSplit(String splitoffId)
    {
        if (!isActivePlayer())
        {
            return;
        }
        game.getActivePlayer().undoSplit(splitoffId);
    }


    void undidSplit(String splitoffId, String survivorId, 
        boolean updateHistory, int turn)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.undidSplit(splitoffId, survivorId, turn);
        }
        if (updateHistory)
        {
            game.history.mergeEvent(splitoffId, survivorId, 
                game.getTurnNumber());
        }
    }

    void undidSplit(String splitoffId, String survivorId)
    {
        undidSplit(splitoffId, survivorId, true, game.getTurnNumber());
    }


    public void undoMove(String markerId)
    {
        if (!isActivePlayer())
        {
            return;
        }
        Legion legion = game.getLegionByMarkerId(markerId);
        String formerHexLabel = legion.getCurrentHexLabel();
        game.getActivePlayer().undoMove(markerId);
        String currentHexLabel = legion.getCurrentHexLabel();
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.undidMove(markerId, formerHexLabel, currentHexLabel);
        }
    }


    public void undoRecruit(String markerId)
    {
        if (!isActivePlayer())
        {
            return;
        }
        game.getActivePlayer().undoRecruit(markerId);
    }


    public void doneWithSplits()
    {
        if (!isActivePlayer())
        {
            Log.error(getPlayerName() + " illegally called doneWithSplits()");
            return;
        }
        if (game.getTurnNumber() == 1 &&
            game.getActivePlayer().getNumLegions() == 1)
        {
            showMessageDialog("Must split initial legion");
            return;
        }
        game.advancePhase(Constants.SPLIT, getPlayerName());
    }

    public void doneWithMoves()
    {
        if (!isActivePlayer())
        {
            Log.error(getPlayerName() + " illegally called doneWithMoves()");
            return;
        }

        Player player = game.getActivePlayer();

        // If any legion has a legal non-teleport move, then
        // the player must move at least one legion.
        if (player.legionsMoved() == 0 &&
            player.countMobileLegions() > 0)
        {
            Log.debug("At least one legion must move.");
            showMessageDialog("At least one legion must move.");
            return;
        }
        // If legions share a hex and have a legal
        // non-teleport move, force one of them to take it.
        else if (player.splitLegionHasForcedMove())
        {
            Log.debug("Split legions must be separated.");
            showMessageDialog("Split legions must be separated.");
            return;
        }
        // Otherwise, recombine all split legions still in
        // the same hex, and move on to the next phase.
        else
        {
            player.recombineIllegalSplits();
            game.advancePhase(Constants.MOVE, getPlayerName());
        }
    }

    public void doneWithEngagements()
    {
        if (!isActivePlayer())
        {
            Log.error(getPlayerName() + 
                " illegally called doneWithEngagements()");
            return;
        }
        // Advance only if there are no unresolved engagements.
        if (game.findEngagements().size() > 0)
        {
            showMessageDialog("Must resolve engagements");
            return;
        }
        game.advancePhase(Constants.FIGHT, getPlayerName());
    }

    public void doneWithRecruits()
    {
        if (!isActivePlayer())
        {
            Log.error(getPlayerName() + 
                " illegally called doneWithRecruits()");
            return;
        }
        Player player = game.getActivePlayer();
        player.commitMoves();

        // Mulligans are only allowed on turn 1.
        player.setMulligansLeft(0);

        game.advancePhase(Constants.MUSTER, getPlayerName());
    }


    // XXX Notify all players.
    public void withdrawFromGame()
    {
        Player player = getPlayer();
        // If player quits while engaged, set slayer.
        String slayerName = null;
        Legion legion = player.getTitanLegion();
        if (legion != null && game.isEngagement(legion.getCurrentHexLabel()))
        {
            slayerName = game.getFirstEnemyLegion(
                legion.getCurrentHexLabel(), player).getPlayerName();
        }
        player.die(slayerName, true);
        if (player == game.getActivePlayer())
        {
            game.advancePhase(game.getPhase(), getPlayerName());
        }
    }


    public void setDonor(String markerId)
    {
        if (!isActivePlayer())
        {
            Log.error(getPlayerName() + " illegally called setDonor()");
            return;
        }
        Player player = game.getActivePlayer();
        Legion donor = game.getLegionByMarkerId(markerId);
        if (donor != null && donor.getPlayer() == player)
        {
            player.setDonor(donor);
        }
        else
        {
            Log.error("Bad arg to Server.getDonor() for " + markerId);
        }
    }


    private List getPlayerInfo(boolean treatDeadAsAlive)
    {
        List info = new ArrayList(game.getNumPlayers());
        Iterator it = game.getPlayers().iterator();
        while (it.hasNext())
        {
            Player player = (Player)it.next();
            info.add(player.getStatusInfo(treatDeadAsAlive));
        }
        return info;
    }


    public void doSplit(String parentId, String childId, String results)
    {
        IClient client = getClient(getPlayerName());
        if (!isActivePlayer())
        {
            Log.error(getPlayerName() + " illegally called doSplit()");
            client.nakSplit(parentId);
            return;
        }
        if (!game.doSplit(parentId, childId, results))
        {
            client.nakSplit(parentId);
        }
    }

    /** Callback from game after this legion was split off. */
    void didSplit(String hexLabel, String parentId, String childId, int height)
    {
        allUpdatePlayerInfo();

        IClient activeClient = getClient(game.getActivePlayerName());

        Legion child = game.getLegionByMarkerId(childId);
        List splitoffs = child.getImageNames();
        activeClient.didSplit(hexLabel, parentId, childId, height, splitoffs,
            game.getTurnNumber());

        game.history.splitEvent(parentId, childId, splitoffs, 
            game.getTurnNumber());

        if (!game.getOption(Options.allStacksVisible))
        {
            splitoffs.clear();
        }

        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            if (client != activeClient)
            {
                client.didSplit(hexLabel, parentId, childId, height, 
                    splitoffs, game.getTurnNumber());
            }
        }
    }

    /** Call from History during load game only */
    void didSplit(String parentId, String childId, List splitoffs, int turn)
    {
        IClient activeClient = getClient(game.getActivePlayerName());
        int childSize = splitoffs.size();
        activeClient.didSplit(null, parentId, childId, childSize, splitoffs, 
            turn);

        if (!game.getOption(Options.allStacksVisible))
        {
            splitoffs.clear();
        }

        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            if (client != activeClient)
            {
                client.didSplit(null, parentId, childId, childSize, splitoffs, 
                    turn);
            }
        }
    }


    public void doMove(String markerId, String hexLabel, String entrySide,
        boolean teleport, String teleportingLord)
    {
        IClient client = getClient(getPlayerName());
        if (!isActivePlayer())
        {
            Log.error(getPlayerName() + " illegally called doMove()");
            client.nakMove(markerId);
            return;
        }
        Legion legion = game.getLegionByMarkerId(markerId);
        String startingHexLabel = legion.getCurrentHexLabel();

        if (game.doMove(markerId, hexLabel, entrySide, teleport,
            teleportingLord))
        {
            allTellDidMove(markerId, startingHexLabel, hexLabel, entrySide,
                teleport);
        }
        else
        {
            client.nakMove(markerId);
        }
    }

    void allTellDidMove(String markerId, String startingHexLabel,
        String endingHexLabel, String entrySide, boolean teleport)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.didMove(markerId, startingHexLabel, endingHexLabel,
                entrySide, teleport);
        }
    }


    void allTellAddCreature(String markerId, String creatureName, 
        boolean updateHistory)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.addCreature(markerId, creatureName);
        }
        if (updateHistory)
        {
            game.history.addCreatureEvent(markerId, creatureName);
        }
    }

    void allTellRemoveCreature(String markerId, String creatureName, 
        boolean updateHistory)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.removeCreature(markerId, creatureName);
        }
        if (updateHistory)
        {
            game.history.removeCreatureEvent(markerId, creatureName);
        }
    }

    void allRevealLegion(Legion legion)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.revealCreatures(legion.getMarkerId(),
                legion.getImageNames());
        }
        game.history.revealEvent(true, null, legion.getMarkerId(), 
            legion.getImageNames(), true);
    }

    /** Call from History during load game only */
    void allRevealLegion(String markerId, List creatureNames)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.revealCreatures(markerId, creatureNames);
        }
    }

    void oneRevealLegion(Legion legion, String playerName)
    {
        IClient client = getClient(playerName);
        if (client != null)
        {
            client.revealCreatures(legion.getMarkerId(), 
                legion.getImageNames());
        }
        List li = new ArrayList();
        li.add(playerName);
        game.history.revealEvent(false, li, legion.getMarkerId(), 
            legion.getImageNames(), true);
    }

    /** Call from History during load game only */
    void oneRevealLegion(String playerName, String markerId, List creatureNames)
    {
        IClient client = getClient(playerName);
        if (client != null)
        {
            client.revealCreatures(markerId, creatureNames);
        }
        List li = new ArrayList();
        li.add(playerName);
    }


    void allFullyUpdateLegionStatus()
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            if (client != null)
            {
                Iterator it2 = game.getAllLegions().iterator();
                while (it2.hasNext())
                {
                    Legion legion = (Legion)it2.next();
                    client.setLegionStatus(legion.getMarkerId(), 
                        legion.hasMoved(), legion.hasTeleported(),
                        legion.getEntrySide(), legion.getRecruitName());
                }
            }
        }
    }

    void allFullyUpdateAllLegionContents()
    {
        Iterator it = game.getAllLegions().iterator();
        while (it.hasNext())
        {
            Legion legion = (Legion)it.next();
            allRevealLegion(legion);
        }
    }

    void allRevealCreature(Legion legion, String creatureName)
    {
        List creatureNames = new ArrayList();
        creatureNames.add(creatureName);
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.revealCreatures(legion.getMarkerId(), creatureNames);
        }
        game.history.revealEvent(true, null, legion.getMarkerId(),
            creatureNames, false);
    }

    /** Call from History during load game only */
    void allRevealCreature(String markerId, String creatureName)
    {
        List creatureNames = new ArrayList();
        creatureNames.add(creatureName);
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.revealCreatures(markerId, creatureNames);
        }
    }


    // XXX Disallow these in network games?
    public void newGame()
    {
        Start.startupDialog(game, null);
    }

    public void loadGame(String filename)
    {
        game.loadGame(filename);
    }

    public void saveGame(String filename)
    {
        game.saveGame(filename);
    }

    /** Used to change a player name after color is assigned. */
    void setPlayerName(String playerName, String newName)
    {
        Log.debug("Server.setPlayerName() from " + playerName + " to " + 
            newName);
        IClient client = getClient(playerName);
        client.setPlayerName(newName);
        clientMap.remove(playerName);
        clientMap.put(newName, client);
    }

    synchronized void askPickColor(String playerName,
                                   final java.util.List colorsLeft)
    {
        IClient client = getClient(playerName);
        if (client != null)
        {
            client.askPickColor(colorsLeft);
        }
    }

    public synchronized void assignColor(String color)
    {
        if (!getPlayerName().equals(game.getNextColorPicker()))
        {
            Log.error(getPlayerName() + " illegally called assignColor()");
            return;
        }
        if (getPlayer() == null || getPlayer().getColor() == null)
        {
            game.assignColor(getPlayerName(), color);
        }
    }

    void askPickFirstMarker(String playerName)
    {
        Player player = game.getPlayer(playerName);
        IClient client = getClient(playerName);
        if (client != null)
        {
            client.askPickFirstMarker();
        }
    }

    public void assignFirstMarker(String markerId)
    {
        Player player = game.getPlayer(getPlayerName());
        if (!player.getMarkersAvailable().contains(markerId))
        {
            Log.error(getPlayerName() + 
                " illegally called assignFirstMarker()");
            return;
        }
        player.setFirstMarker(markerId);
        game.nextPickColor();
    }

    /** Hack to set color on load game. */
    synchronized void allSetColor()
    {
        Iterator it = game.getPlayers().iterator();
        while (it.hasNext())
        {
            Player player = (Player)it.next();
            String name = player.getName();
            String color = player.getColor();
            IClient client = getClient(name);
            if (client != null)
            {
                client.setColor(color);
            }
        }
    }


    // XXX We use Server as a hook for PhaseAdvancer to get to options,
    // but this is ugly.
    int getIntOption(String optname)
    {
        return game.getIntOption(optname);
    }


    void oneSetOption(String playerName, String optname, String value)
    {
        IClient client = getClient(playerName);
        if (client != null)
        {
            client.setOption(optname, value);
        }
    }

    void oneSetOption(String playerName, String optname, boolean value)
    {
        oneSetOption(playerName, optname, String.valueOf(value));
    }

    void allSetOption(String optname, String value)
    {
        Iterator it = clients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.setOption(optname, value);
        }
    }

    void allSetOption(String optname, boolean value)
    {
        allSetOption(optname, String.valueOf(value));
    }

    void allSetOption(String optname, int value)
    {
        allSetOption(optname, String.valueOf(value));
    }

    /** public so that it can be called from Log. */
    public void allLog(String message)
    {
        Iterator it = remoteClients.iterator();
        while (it.hasNext())
        {
            IClient client = (IClient)it.next();
            client.log(message);
        }
    }
}
