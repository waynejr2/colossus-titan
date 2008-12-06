package net.sf.colossus.webclient;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import net.sf.colossus.client.Client;
import net.sf.colossus.game.Game;
import net.sf.colossus.server.Constants;
import net.sf.colossus.server.Start;
import net.sf.colossus.util.KFrame;
import net.sf.colossus.util.Options;
import net.sf.colossus.util.ViableEntityManager;
import net.sf.colossus.webcommon.GameInfo;
import net.sf.colossus.webcommon.IWebClient;
import net.sf.colossus.webcommon.IWebServer;
import net.sf.colossus.webcommon.User;


/** This is the main class for one user client for the web server.
 *  One such client can register and/or login to the web server,
 *  propose a game, browse proposed games and enroll to such a game.
 *  When a game has enough players, it can be started, and this 
 *  brings up the MasterBoard like the network client would do.
 *  
 *  @version $Id$
 *  @author Clemens Katzer
 */

public class WebClient extends KFrame implements WindowListener,
    ActionListener, IWebClient
{
    private static final Logger LOGGER = Logger.getLogger(WebClient.class
        .getName());

    // TODO make this all based on Locale.getDefault()
    // Initially: use German. To make it variable, need also to set
    // the default values / info texts according to Locale.
    final static Locale myLocale = Locale.GERMANY;

    private String hostname;
    private int port;
    private String login;
    private String username;
    private String password;

    private boolean isAdmin = false;

    private final Options options;
    private Client gameClient;

    private RegisterPasswordPanel registerPanel;

    private final static int NotLoggedIn = 1;
    private final static int LoggedIn = 2;
    private final static int Enrolled = 3;
    private final static int Playing = 4;
    // private final static int PlayingButDead = 5;

    // boundaries which port nr user may enter in the ServerPort field:
    private final static int minPort = 1;
    private final static int maxPort = 65535;

    private final static String sep = net.sf.colossus.server.Constants.protocolTermSeparator;

    boolean failedDueToDuplicateLogin = false;

    private int state = NotLoggedIn;
    private String enrolledGameId = null;

    private int usersLoggedIn = 0;
    private int usersEnrolled = 0;
    private int usersPlaying = 0;
    private int usersDead = 0;
    private long usersLogoffAgo = 0;
    private String usersText = "";

    private IWebServer server = null;
    private WebClientSocketThread cst = null;

    JTabbedPane tabbedPane;
    Box serverTab;
    Box instantGamesTab;
    Box adminTab;

    private final Point defaultLocation = new Point(600, 100);

    private JLabel statusLabel;
    private JLabel userinfoLabel; // Server/Login pane:

    private JTextField webserverHostField;
    private JTextField webserverPortField;
    private JTextField loginField;
    private JPasswordField passwordField;

    private JTextField commandField;
    private JLabel receivedField;

    private JButton loginButton;
    private JButton quitButton;

    private JCheckBox autologinCB;
    private JCheckBox autoGamePaneCB;

    private JLabel registerOrPasswordLabel;
    private JButton registerOrPasswordButton;

    private JButton debugSubmitButton;
    private JButton shutdownButton;

    private JLabel statusField;
    private String statusText = "";

    // Game browsing pane:
    private JComboBox variantBox;
    private JComboBox viewmodeBox;
    private JComboBox eventExpiringBox;

    private JSpinner spinner1;
    private JSpinner spinner2;
    private JSpinner spinner3;

    private JCheckBox unlimitedMulligansCB;
    private JCheckBox balancedTowersCB;

    private JButton proposeButton;
    private JButton cancelButton;
    private JButton enrollButton;
    private JButton unenrollButton;
    private JButton startButton;

    private JButton hideButton;
    private JLabel hideButtonText;

    private JRadioButton autoGSNothingRB;
    private JRadioButton autoGSHideRB;
    private JRadioButton autoGSCloseRB;

    // automatic actions when game starts (client masterboard comes up):
    private ButtonGroup autoGSActionGroup;

    JLabel infoTextLabel;
    final static String needLoginText = "You need to login to browse or propose Games.";
    final static String enrollText = "Propose or Enroll, and when enough players have enrolled, one of them can press 'Start'.";
    final static String startingText = "Game is starting, MasterBoard should appear soon. Please wait...";
    final static String startedText = "Game was started...";
    final static String waitingText = "Client connected successfully, waiting for all other players. Please wait...";
    final static String enrolledText = "While enrolled, you can't propose or enroll to other games.";
    final static String playingText = "While playing, you can't propose or enroll to other games.";
    
    ChatHandler generalChat;
    ScheduledGamesTab schedulingPanel;

    final ArrayList<GameInfo> gamesUpdates = new ArrayList<GameInfo>();

    HashMap<String, GameInfo> gameHash = new HashMap<String, GameInfo>();
    
    // potential games:
    JTable potGameTable;
    GameTableModel potGameDataModel;
    ListSelectionModel potGameListSelectionModel;

    // running games
    JTable runGameTable;
    GameTableModel runGameDataModel;
    ListSelectionModel runGameListSelectionModel;

    private static String windowTitle = "Web Client";

    private final static String LoginButtonText = "Login";
    private final static String LogoutButtonText = "Logout";
    private final static String quitButtonText = "Quit";
    private final static String HideButtonText = "Hide Web Client";
    private final static String CantHideText = "(You can hide web client only if game client is open)";
    private final static String HowtoUnhideText = "You can get web client back from MasterBoard - Window menu";

    private final static String createAccountButtonText = "Register";
    private final static String chgPasswordButtonText = "Change password";

    private final static String ProposeButtonText = "Propose";
    private final static String EnrollButtonText = "Enroll";
    private final static String UnenrollButtonText = "Unenroll";
    private final static String CancelButtonText = "Cancel";
    private final static String StartButtonText = "Start";

    private final static String AutoLoginCBText = "Auto-login on start";
    private final static String AutoGamePaneCBText = "After login Game pane";

    private final static String createAccountLabelText = "No login yet? Create one:";
    private final static String chgPasswordLabelText = "Change your password:";

    private final static String AutoGameStartActionNothing = "Do nothing";
    private final static String AutoGameStartActionHide = "Hide WebClient";
    private final static String AutoGameStartActionClose = "Close WebClient";

    private final static String optAutoGameStartAction = "Auto Game Start Action";

    public WebClient(String hostname, int port, String login, String password)
    {
        super(windowTitle);

        options = new Options(Constants.optionsWebClientName);
        options.loadOptions();

        // Initialize those 4 values + username from either given
        // arguments, loaded from cf file, or reasonable default.
        initValues(hostname, port, login, password);

        ViableEntityManager.register(this, "WebClient " + login);
        net.sf.colossus.webcommon.InstanceTracker.register(this, "WebClient "
            + login);

        if (SwingUtilities.isEventDispatchThread())
        {
            setupGUI();
            autoActions();
        }
        else
        {
            try
            {
                SwingUtilities.invokeAndWait(new Runnable()
                {
                    public void run()
                    {
                        setupGUI();
                        autoActions();
                    }
                });
            }
            catch (InterruptedException e)
            {/* ignore */
            }
            catch (InvocationTargetException e2)
            {/* ignore */
            }
        }
    }

    private void initValues(String hostname, int port, String login,
        String password)
    {
        if (hostname != null && !hostname.equals(""))
        {
            this.hostname = hostname;
        }
        else
        {
            String cfHostname = options.getStringOption(Options.webServerHost);
            if (cfHostname != null && !cfHostname.equals(""))
            {
                this.hostname = cfHostname;
            }
            else
            {
                this.hostname = Constants.defaultWebServer;

            }
        }

        if (port > 0)
        {
            this.port = port;
        }
        else
        {
            int cfPort = options.getIntOption(Options.webServerPort);
            if (cfPort >= 1)
            {
                this.port = cfPort;
            }
            else
            {
                this.port = Constants.defaultWebPort;
            }
        }

        // Initialize this already here, password login depends on it.
        String cfLogin = options.getStringOption(Options.webClientLogin);

        // Use -m argument, if given; otherwise from cf or default.
        if (login != null && !login.equals(""))
        {
            this.login = login;
        }
        else
        {
            if (cfLogin != null && !cfLogin.equals(""))
            {
                this.login = cfLogin;
            }
            else
            {
                this.login = Constants.username;
            }
        }
        // Right now, login and username are the same, but we may change
        // that one point.
        this.username = this.login;

        // right now password is never given but...
        if (password != null)
        {
            this.password = password;
        }
        else
        {
            String cfPassword = options
                .getStringOption(Options.webClientPassword);
            if (cfPassword != null && !cfPassword.equals("")
                &&
                // Use stored password only if its same user:
                cfLogin != null && cfLogin.equals(this.login)
                && !this.login.equals(""))
            {
                this.password = cfPassword;
            }
            else
            {
                this.password = "";
            }
        }
    }

    public void setGameClient(Client c)
    {
        this.gameClient = c;
        if (c == null)
        {
            hideButton.setEnabled(false);
            hideButtonText.setText(CantHideText);
            if (state == Playing)
            {
                state = LoggedIn;
                enrolledGameId = "";
                updateGUI();
            }
        }
        else
        {
            hideButton.setEnabled(true);
            hideButtonText.setText(HowtoUnhideText);
        }
    }

    public void onGameStartAutoAction()
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            doAutoGSAction();
        }
        else
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    doAutoGSAction();
                }
            });
        }
    }

    private void setupGUI()
    {
        getContentPane().setLayout(new BorderLayout());

        Box headPane = new Box(BoxLayout.Y_AXIS);
        getContentPane().add(headPane, BorderLayout.NORTH);

        statusLabel = new JLabel("login status");
        userinfoLabel = new JLabel("user info status");
        headPane.add(statusLabel);
        headPane.add(userinfoLabel);

        Box mainPane = new Box(BoxLayout.Y_AXIS);
        getContentPane().add(mainPane, BorderLayout.CENTER);

        tabbedPane = new JTabbedPane();
        tabbedPane.setPreferredSize(new Dimension(900, 600)); // width x height
        tabbedPane.setMinimumSize(new Dimension(900, 530)); // width x height
        mainPane.add(tabbedPane);

        // ========== Server Tab ===============
        serverTab = new Box(BoxLayout.Y_AXIS);
        tabbedPane.addTab("Server", serverTab);

        Box connectionPane = new Box(BoxLayout.X_AXIS);

        JPanel loginPane = new JPanel(new GridLayout(0, 2));
        loginPane.setBorder(new TitledBorder("Connection information"));

        loginPane.setPreferredSize(new Dimension(150, 200));

        loginPane.add(new JLabel("Web Server"));
        webserverHostField = new JTextField(this.hostname);
        // webserverHostField.addActionListener(this);
        loginPane.add(webserverHostField);

        loginPane.add(new JLabel("Port"));
        webserverPortField = new JTextField(this.port + "");
        // webserverPortField.addActionListener(this);
        loginPane.add(webserverPortField);

        loginPane.add(new JLabel("Login id"));
        loginField = new JTextField(this.login);
        // nameField.addActionListener(this);
        loginPane.add(loginField);

        loginPane.add(new JLabel("Password"));
        passwordField = new JPasswordField(this.password);
        // passwordField.addActionListener(this);
        loginPane.add(passwordField);

        loginButton = new JButton(LoginButtonText);
        loginButton.addActionListener(this);
        loginButton.setEnabled(true);
        loginPane.add(loginButton);

        quitButton = new JButton(quitButtonText);
        quitButton.addActionListener(this);
        quitButton.setEnabled(true);
        loginPane.add(quitButton);

        loginPane.add(new JLabel("Status:"));
        statusField = new JLabel(statusText);
        loginPane.add(statusField);
        updateStatus("Not connected", Color.red);

        serverTab.add(connectionPane);

        connectionPane.add(loginPane);
        connectionPane.add(Box.createHorizontalGlue());
        connectionPane.add(Box.createVerticalGlue());

        serverTab.add(Box.createVerticalGlue());
        serverTab.add(Box.createHorizontalGlue());

        boolean alos = this.options.getOption(AutoLoginCBText);
        autologinCB = new JCheckBox(AutoLoginCBText, alos);
        autologinCB.addActionListener(this);
        loginPane.add(autologinCB);
        loginPane.add(new JLabel(""));

        boolean algp = this.options.getOption(AutoGamePaneCBText);
        autoGamePaneCB = new JCheckBox(AutoGamePaneCBText, algp);
        autoGamePaneCB.addActionListener(this);
        loginPane.add(autoGamePaneCB);
        loginPane.add(new JLabel(""));

        // Label can show: registerLabelText or chgPasswordLabelText 
        registerOrPasswordLabel = new JLabel(createAccountLabelText);
        // Button can show: createAccountButtonText or chgPasswordButtonText
        registerOrPasswordButton = new JButton(createAccountButtonText);
        registerOrPasswordButton.addActionListener(this);

        loginPane.add(registerOrPasswordLabel);
        loginPane.add(registerOrPasswordButton);

        // ======= instant Games tab =========
        
        instantGamesTab = new Box(BoxLayout.Y_AXIS);
        tabbedPane.addTab("Instant Games", instantGamesTab);
        
        JPanel preferencesPane = new JPanel(new GridLayout(0, 2));
        preferencesPane.setBorder(new TitledBorder("Game preferences"));

        // Variant:
        String variantName = options.getStringOption(Options.variant);
        if (variantName == null || variantName.length() == 0)
        {
            variantName = Constants.variantArray[0]; // Default variant
        }

        variantBox = new JComboBox(Constants.variantArray);
        variantBox.setSelectedItem(variantName);
        variantBox.addActionListener(this);
        preferencesPane.add(new JLabel("Select variant:"));
        preferencesPane.add(variantBox);

        // Viewmode:
        // String viewmodesArray[] = { "all-public", "auto-tracked", "only-own" };
        String viewmodeName = options.getStringOption(Options.viewMode);
        if (viewmodeName == null)
        {
            viewmodeName = Options.viewableAll;
        }

        viewmodeBox = new JComboBox(Options.viewModeArray);
        viewmodeBox.setSelectedItem(viewmodeName);
        viewmodeBox.addActionListener(this);
        preferencesPane.add(new JLabel("Select view mode:"));
        preferencesPane.add(viewmodeBox);

        // event expiring policy: 
        String eventExpiringVal = options
            .getStringOption(Options.eventExpiring);
        if (eventExpiringVal == null)
        {
            eventExpiringVal = "5";
        }

        eventExpiringBox = new JComboBox(Options.eventExpiringChoices);
        eventExpiringBox.setSelectedItem(eventExpiringVal);
        eventExpiringBox.addActionListener(this);
        preferencesPane.add(new JLabel("Events expire after (turns):"));
        preferencesPane.add(eventExpiringBox);

        // checkboxes (unlimited mulligans and balanced tower):
        Box checkboxPane = new Box(BoxLayout.X_AXIS);
        boolean unlimitedMulligans = options
            .getOption(Options.unlimitedMulligans);
        unlimitedMulligansCB = new JCheckBox(Options.unlimitedMulligans,
            unlimitedMulligans);
        unlimitedMulligansCB.addActionListener(this);
        boolean balancedTowers = options.getOption(Options.balancedTowers);
        balancedTowersCB = new JCheckBox(Options.balancedTowers,
            balancedTowers);
        balancedTowersCB.addActionListener(this);
        checkboxPane.add(unlimitedMulligansCB);
        checkboxPane.add(balancedTowersCB);

        preferencesPane.add(new JLabel("Various settings:"));
        preferencesPane.add(checkboxPane);

        // min, target and max nr. of players:
        preferencesPane.add(new JLabel("Select player count preferences:"));
        Box playerSelection = new Box(BoxLayout.X_AXIS);

        int min = options.getIntOption(Options.minPlayersWeb);
        min = (min < 2 || min > 6 ? 2 : min);

        int max = options.getIntOption(Options.maxPlayersWeb);
        max = (max < min || max > 6 ? 6 : max);

        int middle = java.lang.Math.round(((float)min + (float)max) / 2);

        int targ = options.getIntOption(Options.targPlayersWeb);
        targ = (targ < min || targ > max ? middle : targ);

        playerSelection.add(new JLabel("min.:"));
        SpinnerNumberModel model = new SpinnerNumberModel(min, 2, 6, 1);
        spinner1 = new JSpinner(model);
        playerSelection.add(spinner1);
        // spinner uses ChangeListener instead of ActionListener.
        // Setting them up is quite laborous, and would be called every time
        // user modifies it by one. So, we rather query the value then when we
        // need it (before exiting (saveOptions) or before propose).
        //spinner.addActionListener(this);

        playerSelection.add(new JLabel("target.:"));
        SpinnerNumberModel model2 = new SpinnerNumberModel(targ, 2, 6, 1);
        spinner2 = new JSpinner(model2);
        playerSelection.add(spinner2);

        playerSelection.add(new JLabel("max.:"));
        SpinnerNumberModel model3 = new SpinnerNumberModel(max, 2, 6, 1);
        spinner3 = new JSpinner(model3);
        playerSelection.add(spinner3);

        preferencesPane.add(playerSelection);
        
        instantGamesTab.add(preferencesPane);

        // done with game preferences.
        // Now the buttons and table:
        JPanel startgamePane = new JPanel(new GridLayout(0, 3));
        startgamePane.setBorder(new TitledBorder("Start a game"));

        startgamePane.add(new JLabel("Propose a game:"));
        proposeButton = new JButton(ProposeButtonText);
        proposeButton.addActionListener(this);
        proposeButton.setEnabled(false);
        startgamePane.add(proposeButton);

        cancelButton = new JButton(CancelButtonText);
        cancelButton.addActionListener(this);
        cancelButton.setEnabled(false);
        startgamePane.add(cancelButton);

        startgamePane.add(new JLabel("Join/Leave a game:"));
        enrollButton = new JButton(EnrollButtonText);
        enrollButton.addActionListener(this);
        enrollButton.setEnabled(false);
        startgamePane.add(enrollButton);

        unenrollButton = new JButton(UnenrollButtonText);
        unenrollButton.addActionListener(this);
        unenrollButton.setEnabled(false);
        startgamePane.add(unenrollButton);

        instantGamesTab.add(startgamePane);

        // ====================== Proposed Games ======================

        Box potGamesPane = new Box(BoxLayout.Y_AXIS);
        potGamesPane.setBorder(new TitledBorder("Proposed Games"));
        JLabel dummyField = new JLabel(
            "The following games are accepting players:");
        potGamesPane.add(dummyField);

        potGameDataModel = new GameTableModel();
        potGameTable = new JTable(potGameDataModel);

        potGameListSelectionModel = potGameTable.getSelectionModel();
        potGameListSelectionModel
            .addListSelectionListener(new GameTableSelectionHandler());
        
        // TODO is that setting again needed?
        potGameTable.setSelectionModel(potGameListSelectionModel);

        potGameTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane tablescrollpane = new JScrollPane(potGameTable);
        potGamesPane.add(tablescrollpane);

        startButton = new JButton(StartButtonText);
        startButton.addActionListener(this);
        startButton.setEnabled(false);
        potGamesPane.add(startButton);

        infoTextLabel = new JLabel(enrollText);
        potGamesPane.add(infoTextLabel);

        instantGamesTab.add(potGamesPane);

        
        // ================== Scheduled Games tab ======================
        
        schedulingPanel = new ScheduledGamesTab(this, myLocale);
        tabbedPane.addTab("Scheduled Games", schedulingPanel);
        
        // ====================== Running Games Tab ======================

        // ----------------- First the table ---------------------
        
        Box runningGamesTab = new Box(BoxLayout.Y_AXIS);
        tabbedPane.addTab("Running Games", runningGamesTab);

        Box runningGamesPane = new Box(BoxLayout.Y_AXIS);
        // runningGamesPane.setAlignmentY(0);
        runningGamesPane.setBorder(new TitledBorder("Running Games"));
        runningGamesPane.add(new JLabel(
            "The following games are already running:"));

        runGameDataModel = new GameTableModel();

        runGameTable = new JTable(runGameDataModel);

        runGameListSelectionModel = runGameTable.getSelectionModel();
        runGameListSelectionModel
            .addListSelectionListener(new GameTableSelectionHandler());
        runGameTable.setSelectionModel(runGameListSelectionModel);

        runGameTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane runtablescrollpane = new JScrollPane(runGameTable);
        runningGamesPane.add(runtablescrollpane);

        runningGamesTab.add(runningGamesPane);


        // ------------------ Hide WebClient stuff ---------------
        
        Box hideClientPanel = new Box(BoxLayout.Y_AXIS);
        hideClientPanel.setBorder(new TitledBorder("Hiding the Web Client"));
        
        runningGamesTab.add(Box.createRigidArea(new Dimension(0, 20)));
        runningGamesTab.add(Box.createVerticalGlue());
        
        hideClientPanel.setAlignmentX(Box.LEFT_ALIGNMENT);
        
        hideButton = new JButton(HideButtonText);
        hideButton.setAlignmentX(Box.LEFT_ALIGNMENT);

        hideButton.addActionListener(this);
        hideButton.setEnabled(false);
        hideClientPanel.add(hideButton);
        hideButtonText = new JLabel(CantHideText);
        hideClientPanel.add(hideButtonText);

        // automatic actions when game starts (client masterboard comes up):
        JLabel autoDoLabel = new JLabel("When game starts, automatically:");
        autoDoLabel.setAlignmentX(Box.LEFT_ALIGNMENT);
        hideClientPanel.add(autoDoLabel);
        Box autoDoButtonPane = new Box(BoxLayout.X_AXIS);
        autoGSNothingRB = new JRadioButton(AutoGameStartActionNothing);
        autoGSHideRB = new JRadioButton(AutoGameStartActionHide);
        autoGSCloseRB = new JRadioButton(AutoGameStartActionClose);
        autoDoButtonPane.add(autoGSNothingRB);
        autoDoButtonPane.add(autoGSHideRB);
        autoDoButtonPane.add(autoGSCloseRB);

        autoGSNothingRB.addActionListener(this);
        autoGSHideRB.addActionListener(this);
        autoGSCloseRB.addActionListener(this);

        autoGSActionGroup = new ButtonGroup();
        autoGSActionGroup.add(autoGSNothingRB);
        autoGSActionGroup.add(autoGSHideRB);
        autoGSActionGroup.add(autoGSCloseRB);

        String autoGSAction = options.getStringOption(optAutoGameStartAction);
        if (autoGSAction == null || autoGSAction.equals(""))
        {
            autoGSAction = AutoGameStartActionNothing;
            options.setOption(optAutoGameStartAction, autoGSAction);
        }

        if (autoGSAction.equals(AutoGameStartActionNothing))
        {
            autoGSNothingRB.setSelected(true);
        }
        else if (autoGSAction.equals(AutoGameStartActionHide))
        {
            autoGSHideRB.setSelected(true);
        }
        else if (autoGSAction.equals(AutoGameStartActionClose))
        {
            autoGSCloseRB.setSelected(true);
        }
        else
        {
            autoGSAction = AutoGameStartActionNothing;
            options.setOption(optAutoGameStartAction, autoGSAction);
            autoGSNothingRB.setSelected(true);
        }

        autoDoButtonPane.setAlignmentX(Box.LEFT_ALIGNMENT);
        hideClientPanel.add(autoDoButtonPane);
        hideClientPanel.add(Box.createVerticalGlue());

        runningGamesTab.add(Box.createVerticalGlue());
        runningGamesTab.add(hideClientPanel);

/*      // Somehow this does not work at all...

        // as wide as the running games table, as high as needed:
        int width = runningGamesPane.getMinimumSize().width;
        int height = hideClientPanel.getMinimumSize().height;
        Dimension prefSize = new Dimension(width, height);
        hideClientPanel.setPreferredSize(prefSize);
        hideClientPanel.setMinimumSize(prefSize);
*/                
        
        // ================== "General" Chat tab ======================

        generalChat = new ChatHandler(IWebServer.generalChatName, "Chat",
            this, server, username);
        tabbedPane.addTab(generalChat.getTitle(), generalChat.getTab());
        
        // ============admin Tab ==========

        adminTab = new Box(BoxLayout.Y_AXIS);

        JPanel adminPane = new JPanel(new GridLayout(0, 1));
        adminPane.setBorder(new TitledBorder("Admin mode"));
        adminPane.setPreferredSize(new Dimension(30, 200));

        commandField = new JTextField("");
        adminPane.add(commandField);

        debugSubmitButton = new JButton("Submit");
        debugSubmitButton.addActionListener(this);
        adminPane.add(debugSubmitButton);
        debugSubmitButton.setEnabled(false);

        adminPane.add(new JLabel("Server answered:"));
        receivedField = new JLabel("");
        adminPane.add(receivedField);

        shutdownButton = new JButton("Shutdown Server");
        shutdownButton.addActionListener(this);
        adminPane.add(shutdownButton);

        adminTab.add(adminPane);
        
        // adminTab is added to tabbedPane then/only when user has
        // logged in and server informed us that this user as admin user

        // ============== finish all ================
        addWindowListener(this);
        pack();

        useSaveWindow(options, "WebClient", defaultLocation);
        setVisible(true);
    }

    private void autoActions()
    {
        if (autologinCB.isSelected())
        {
            String login = loginField.getText();
            String password = new String(passwordField.getPassword());

            // Eclipse warning says password can never be null. Well...
            if (login != null && !login.equals("") && !password.equals(""))
            {
                doLogin();
            }
        }
        else
        {
            actualUpdateGUI();
        }
    }

    private void doAutoGSAction()
    {
        String whatToDo = options.getStringOption(optAutoGameStartAction);
        if (whatToDo == null)
        {
            return;
        }

        if (whatToDo.equals(AutoGameStartActionNothing))
        {
            // ok, nothing to do.
        }
        else if (whatToDo.equals(AutoGameStartActionHide))
        {
            this.setVisible(false);
        }
        else if (whatToDo.equals(AutoGameStartActionClose))
        {
            Start startObj = Start.getCurrentStartObject();
            startObj.setWhatToDoNext(Start.GetPlayersDialog);
            dispose();
        }
        else
        {
            LOGGER.log(Level.WARNING,
                "ooops! auto Game Start Action option is '" + whatToDo
                    + "' ???");
        }
    }

    private void updateStatus(String text, Color color)
    {
        this.statusText = text;
        statusField.setText(text);
        statusField.setForeground(color);
    }

    private void addAdminTab()
    {
        tabbedPane.addTab("Admin", adminTab);
    }

    private void removeAdminTab()
    {
        tabbedPane.remove(adminTab);
    }

    private void setAdmin(boolean isAdmin)
    {
        this.isAdmin = isAdmin;
        if (this.isAdmin)
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    addAdminTab();
                }
            });
        }
        else
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    removeAdminTab();
                }
            });
        }
    }

    public boolean isAdmin()
    {
        return isAdmin;
    }

    public void showAnswer(String s)
    {
        receivedField.setText(s);
    }

    public String getHost()
    {
        return webserverHostField.getText();
    }

    public String getPort()
    {
        return webserverPortField.getText();
    }

    public  String createLoginWebClientSocketThread(boolean force)
    {
        String reason = null;
        failedDueToDuplicateLogin = false;

        // email is null: WCST does login
        cst = new WebClientSocketThread(this, hostname, port, username,
            password, force, null);
        WebClientSocketThread.WebClientSocketThreadException e = cst
            .getException();
        if (e == null)
        {
            cst.start();
            server = cst;

            updateStatus("Logged in", Color.green);
        }
        else
        {
            // I would have liked to let the constructor throw an exception
            // and catch this here, but then the returned pointer was null,
            // so could not do anything with it (and start() not be run),
            // so GC did not clean it up. Sooo... let's do it this way,
            // a little bit clumsy...

            if (cst.stillNeedsRun())
            {
                cst.start();
            }

            cst = null;
            server = null;

            reason = e.getMessage();

            if (reason == null)
            {
                reason = "Unknown reason";
            }

            // if it is the duplicate login case: if force was not set, give
            // user a 2nd chance to login with force, without the original message.
            if (!force && e.failedBecauseAlreadyLoggedIn())
            {
                failedDueToDuplicateLogin = true;
                return reason;
            }

            // otherwise just show what's wrong
            JOptionPane.showMessageDialog(this, reason);
            updateStatus("Login failed", Color.red);
            return reason;
        }

        return reason;
    }

    public String createRegisterWebClientSocketThread(String username,
        String password, String email)
    {
        String reason = null;
        boolean force = false; // dummy

        // email is NOT null: WCST does register first instead of login
        cst = new WebClientSocketThread(this, hostname, port, username,
            password, force, email);

        WebClientSocketThread.WebClientSocketThreadException e = cst
            .getException();
        if (e == null)
        {
            cst.start();
            server = cst;
            JOptionPane.showMessageDialog(registerPanel,
                "Account was created successfully.", "Registration OK",
                JOptionPane.INFORMATION_MESSAGE);
            loginField.setText(username);
            passwordField.setText(password);
            WebClient.this.login = username;
            WebClient.this.username = username;
            WebClient.this.password = password;

            updateStatus("Successfully registered", Color.green);
            registerPanel.dispose();
        }
        else
        {
            reason = e.getMessage();

            if (reason == null)
            {
                reason = "Unknown reason";
            }

            updateStatus("Registration/login failed", Color.red);

            JOptionPane.showMessageDialog(registerPanel, reason);
            return reason;
        }

        return reason;
    }

    private boolean logout()
    {
        boolean success = false;

        server.logout();
        server = null;
        cst = null;

        updateStatus("Not connected", Color.red);
        return success;
    }

    private void doQuit()
    {
        Client gc = gameClient;

        if (gc != null)
        {
            // Game client handles confirmation if necessary, 
            // asks what to do next, and sets startObj accordingly,
            // and it also disposes this WebClient window.
            gc.doConfirmAndQuit();
            gc = null;
        }
        else
        {
            Start startObj = Start.getCurrentStartObject();
            startObj.setWhatToDoNext(Start.QuitAll);
            Start.triggerTimedQuit();
            dispose();
        }
    }

    @Override
    public void dispose()
    {
        // we have a server ( = a WebClientSocketThread) 
        // if and only if we are logged in.
        if (server != null)
        {
            doLogout();
        }

        super.dispose();

        int min = ((Integer)spinner1.getValue()).intValue();
        int target = ((Integer)spinner2.getValue()).intValue();
        int max = ((Integer)spinner3.getValue()).intValue();
        options.setOption(Options.minPlayersWeb, min);
        options.setOption(Options.maxPlayersWeb, max);
        options.setOption(Options.targPlayersWeb, target);

        // options.setStringOption(Options.)
        options.saveOptions();

        if (gameClient != null)
        {
            gameClient.setWebClient(null);
            gameClient = null;
        }

        gameHash.clear();
        synchronized (gamesUpdates)
        {
            gamesUpdates.clear();
        }

        ViableEntityManager.unregister(this);
    }

    private String getUserinfoText()
    {
        String text;
        if (state == NotLoggedIn)
        {
            text = "<unknown>";
        }
        else if (usersLoggedIn <= 1)
        {
            text = "No other users logged in.";
        }
        else
        {
            text = usersLoggedIn + " logged in.";
            // just to get rid of the "never read locally" warning...:
            String dummy = (usersEnrolled + usersPlaying + usersDead + usersLogoffAgo)
                + usersText;
            LOGGER.log(Level.FINEST, "Loggedin: " + usersLoggedIn
                + ", others dummy: " + dummy);
            // // Server doesn't tell actual values for the other stuff yet.
            // text = usersLoggedIn + " logged in, of that " +
            //     usersEnrolled + " enrolled, " +
            //     usersPlaying + "playing and " +
            //     usersDead + "playing but eliminated.";
        }
        return text;
    }

    public void updateGUI()
    {
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                actualUpdateGUI();
            }
        });

    }

    // this should always be called inside a invokeLater (i.e. in EDT)!!
    public void actualUpdateGUI()
    {
        // Many settings are only "loggedIn or not" specific - those first:
        if (state == NotLoggedIn)
        {
            loginButton.setText(LoginButtonText);
            generalChat.setLoginState(false, server, username);
            schedulingPanel.setLoginState(false);
            
            registerOrPasswordLabel.setText(createAccountLabelText);
            registerOrPasswordButton.setText(createAccountButtonText);
        }
        else
        {
            loginButton.setText(LogoutButtonText);
            generalChat.setLoginState(true, server, username);
            schedulingPanel.setLoginState(true);
            registerOrPasswordLabel.setText(chgPasswordLabelText);
            registerOrPasswordButton.setText(chgPasswordButtonText);
        }

        // Now the ones which are more specific
        if (state == NotLoggedIn)
        {
            proposeButton.setEnabled(false);
            enrollButton.setEnabled(false);
            unenrollButton.setEnabled(false);
            cancelButton.setEnabled(false);
            startButton.setEnabled(false);
            debugSubmitButton.setEnabled(false);
            potGameTable.setEnabled(true);

            infoTextLabel.setText(needLoginText);
            statusLabel.setText("Status: not logged in");
            userinfoLabel.setText("Userinfo: " + getUserinfoText());
            this.setTitle(windowTitle + "(not logged in)");
        }
        else if (state == LoggedIn)
        {
            int selRow = potGameTable.getSelectedRow();
            if (selRow == -1)
            {
                enrollButton.setEnabled(false);
                cancelButton.setEnabled(false);
            }
            else
            {
                enrollButton.setEnabled(true);
                if (isOwner(selRow))
                {
                    cancelButton.setEnabled(true);
                }
                else
                {
                    cancelButton.setEnabled(false);
                }
            }
            startButton.setEnabled(false);
            proposeButton.setEnabled(true);
            unenrollButton.setEnabled(false);

            debugSubmitButton.setEnabled(true);
            potGameTable.setEnabled(true);

            infoTextLabel.setText(enrollText);
            statusLabel.setText("Status: logged in as " + username);
            userinfoLabel.setText("Userinfo: " + getUserinfoText());
            this.setTitle(windowTitle + " " + username + " (logged in)");

        }
        else if (state == Enrolled)
        {
            proposeButton.setEnabled(false);
            enrollButton.setEnabled(false);
            unenrollButton.setEnabled(true);
            debugSubmitButton.setEnabled(true);
            potGameTable.setEnabled(false);
            cancelButton.setEnabled(false);

            GameInfo gi = findGameById(enrolledGameId);
            if (gi != null)
            {
                if (gi.getEnrolledCount().intValue() >= gi.getMin().intValue())
                {
                    startButton.setEnabled(true);
                }

                else
                {
                    startButton.setEnabled(false);
                }
            }
            else
            {
                LOGGER.log(Level.WARNING,
                    "Huuuh? UpdateGUI, get game from hash null??");
            }

            infoTextLabel.setText(enrolledText);
            userinfoLabel.setText("Userinfo: " + getUserinfoText());
            statusLabel.setText("Status: As " + username
                + " - enrolled to game " + enrolledGameId);
            this.setTitle(windowTitle + " " + username + " (enrolled)");
        }
        else if (state == Playing)
        {
            proposeButton.setEnabled(false);
            enrollButton.setEnabled(false);
            unenrollButton.setEnabled(false);
            cancelButton.setEnabled(false);
            startButton.setEnabled(false);
            debugSubmitButton.setEnabled(false);
            potGameTable.setEnabled(true);

            infoTextLabel.setText(playingText);
            userinfoLabel.setText("Userinfo: " + getUserinfoText());
            statusLabel.setText("Status: As " + username + " - playing game "
                + enrolledGameId);
            this.setTitle(windowTitle + " " + username + " (playing)");
        }
        else
        {
            LOGGER.log(Level.WARNING, "Web Client - Bogus state " + state);
        }
    }

    // SocketThread needs this to find games when "reinstantiating" it
    // from tokens got from server
    public HashMap<String, GameInfo> getGameHash()
    {
        return gameHash;
    }

    private GameInfo findGameById(String gameId)
    {
        GameInfo gi = gameHash.get(gameId);
        if (gi == null)
        {
            LOGGER.log(Level.SEVERE, "Game from hash is null!!");
        }

        return gi;
    }

    private boolean isOwner(int row)
    {
        String initiator = (String)potGameTable.getValueAt(row, 2);
        if (username.equals(initiator))
        {
            return true;
        }
        return false;
    }

    /* Validate that the given field does not contain any substring which
     * could cause a wrong splitting it by separator at the recipients side.
     * 
     * As the separator currently is " ~ ", in practice this means
     * the userName and password must not start or end with whitespaces
     * or the '~' character, nor contain the separator as a whole.
     * 
     * If invalid, displays a message box telling what is wrong and returns
     * false; if valid, returns true.
     */

    public boolean validateField(Component parent, String content,
        String fieldName)
    {
        String problem = null;
        String temp = content.trim();

        if (!temp.equals(content))
        {
            problem = fieldName + " must not start or end with whitespaces!";
        }
        else if (temp.equalsIgnoreCase(""))
        {
            problem = fieldName + " is missing!";
        }
        else if (temp.equalsIgnoreCase("null"))
        {
            problem = fieldName
                + " must not be the string 'null', no matter which case!";
        }
        else if (content.indexOf(sep) != -1)
        {
            problem = fieldName + " must not contain the string '" + sep
                + "'!";
        }
        else
        {
            for (int i = 0; i < sep.length() && problem == null; i++)
            {
                String critChar = sep.substring(i, i + 1);

                if (content.startsWith(critChar))
                {
                    problem = fieldName + " must not start with '" + critChar
                        + "'!";
                }
                else if (content.endsWith(critChar))
                {
                    problem = fieldName + " must not end with '" + critChar
                        + "'!";
                }
            }
        }

        if (problem != null)
        {
            JOptionPane.showMessageDialog(parent, problem);
            return false;
        }
        return true;
    }

    boolean validatePort(Component parent, String portText)
    {
        boolean ok = true;
        int port = -1;
        try
        {
            port = Integer.parseInt(portText);
            if (port < minPort || port > maxPort)
            {
                ok = false;
            }
        }
        catch (Exception e)
        {
            ok = false;
        }

        if (!ok)
        {
            JOptionPane.showMessageDialog(parent, "Invalid port number!");
        }
        return ok;
    }

    public void doLogin()
    {
        boolean ok = true;
        String portText = webserverPortField.getText();

        ok = ok && validatePort(this, portText);
        ok = ok
            && validateField(this, webserverHostField.getText(), "Host name");
        ok = ok && validateField(this, loginField.getText(), "Login name");
        ok = ok
            && validateField(this, new String(passwordField.getPassword()),
                "Password");

        if (!ok)
        {
            return;
        }

        this.port = Integer.parseInt(portText);
        this.hostname = webserverHostField.getText();
        this.login = loginField.getText();
        this.username = this.login;
        this.password = new String(passwordField.getPassword());

        // first try without force
        String message = createLoginWebClientSocketThread(false);
        if (message != null && failedDueToDuplicateLogin)
        {
            Object[] options = { "Force", "Cancel" };
            int answer = JOptionPane.showOptionDialog(this,
                "Server has already/still another connection open with "
                    + "that login name. Click Force to forcefully logout the "
                    + "other connection, or Cancel to abort.",
                "WebClient login: Force logout of other connection?",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[1]);

            if (answer == 0)
            {
                // if user clicked 'Force', try to connect/login with force argument
                message = createLoginWebClientSocketThread(true);
            }
        }
        if (message == null)
        {
            state = LoggedIn;
            loginField.setEnabled(false);
            updateGUI();

            options.setOption(Options.webServerHost, this.hostname);
            options.setOption(Options.webServerPort, this.port);
            options.setOption(Options.webClientLogin, this.login);
            options.setOption(Options.webClientPassword, this.password);

            options.saveOptions();
            if (autoGamePaneCB.isSelected())
            {
                tabbedPane.setSelectedComponent(instantGamesTab);
            }
        }
        else
        {
            LOGGER.log(Level.FINEST, "connect/login failed...");
        }
    }

    public void doLogout()
    {
        if (state == Enrolled)
        {
            doUnenroll(enrolledGameId);
        }

        logout();
        state = NotLoggedIn;
        setAdmin(false);
        loginField.setEnabled(true);

        updateGUI();
        options.setOption(Options.webClientLogin, this.login);
        options.setOption(Options.webClientPassword, this.password);

        options.saveOptions();
    }

    private void doRegisterOrPasswordDialog(boolean register)
    {
        String username = loginField.getText();
        registerPanel = new RegisterPasswordPanel(this, register, username);
        registerPanel.doIt();
    }

    public String tryChangePassword(String name, String oldPW, String newPW1)
    {
        // email and isAdminObj are null, this signals: do not change them
        String email = null;
        Boolean isAdminObj = null;
        
        String reason = server.changeProperties(name, oldPW,
            newPW1, email, isAdminObj);

        if (reason == null || reason.equals("null"))
        {
            passwordField.setText(newPW1);
            password = newPW1;
            // all went fine, panel shows ok.
            return null;
        }
        else
        {
            // panel shows failure message and reason
            return reason;
        }
    }

    private void doCancel(String gameId)
    {
        server.cancelGame(gameId, username);
        updateGUI();
    }

    private void do_proposeGame(String variant, String viewmode,
        String expire, boolean unlimMulli, boolean balTowers, int min,
        int target, int max)
    {
        server.proposeGame(username, variant, viewmode, expire, unlimMulli,
            balTowers, min, target, max);
    }

    private boolean doEnroll(String gameId)
    {
        server.enrollUserToGame(gameId, username);
        return true;
    }

    private boolean doUnenroll(String gameId)
    {
        server.unenrollUserFromGame(gameId, username);
        return true;
    }

    boolean doStart(String gameId)
    {
        startButton.setEnabled(false);
        server.startGame(gameId);

        return true;
    }

    public void doScheduling(long startTime, int duration, String summary)
    {
        System.out.println("Scheduled game at "
            + startTime+ " duration " + duration + " summary '" + summary + "'");
        server.scheduleGame(username, startTime, duration, summary);
    }
    
    
    // ================= those come from server ============

    public void grantAdminStatus()
    {
        setAdmin(true);
    }

    public void didEnroll(String gameId, String user)
    {
        state = Enrolled;
        enrolledGameId = gameId;

        int index = potGameDataModel.getRowIndex(gameId).intValue();
        potGameTable.setRowSelectionInterval(index, index);
        updateGUI();
    }

    public void didUnenroll(String gameId, String user)
    {
        // do not set it back to LoggedIn if this didUnenroll is the 
        // result of a automatic unenroll before logout
        if (state != NotLoggedIn)
        {
            state = LoggedIn;
        }
        enrolledGameId = "";

        updateGUI();
    }

    public void gameStartsSoon(String gameId)
    {
        startButton.setEnabled(false);
        infoTextLabel.setText(startingText);
    }

    public void gameStarted(String gameId)
    {
        startButton.setEnabled(false);
        infoTextLabel.setText(startedText);
    }

    private final Object comingUpMutex = new Object();
    private boolean clientIsUp = false;

    // Client calls this
    public void notifyComingUp()
    {
        synchronized (comingUpMutex)
        {
            clientIsUp = true;
            comingUpMutex.notify();
        }
    }

    private boolean timeIsUp = false;

    private Timer setupTimer()
    {
        // java.util.Timer, not Swing Timer
        Timer timer = new Timer();
        timeIsUp = false;
        long timeout = 60; // secs

        timer.schedule(new TriggerTimeIsUp(), timeout * 1000);
        return timer;
    }

    class TriggerTimeIsUp extends TimerTask
    {
        @Override
        public void run()
        {
            timeIsUp = true;
            synchronized (comingUpMutex)
            {
                comingUpMutex.notify();
            }
        }
    }

    public void gameStartsNow(String gameId, int port)
    {
        Client gc;
        try
        {
            int p = port;

            // a hack to pass something into the Client constructor
            // TODO needs to be constructed properly
            Game dummyGame = new Game(null, new String[0]);
            boolean noOptionsFile = false;
            gc = new Client(hostname, p, dummyGame, username, null, true,
                noOptionsFile);
            boolean failed = gc.getFailed();
            if (failed)
            {
                gc = null;
                JOptionPane.showMessageDialog(this,
                    "Connecting to the game server "
                    + "(starting own MasterBoard) failed!",
                    "Starting game failed!",
                    JOptionPane.ERROR_MESSAGE);

                state = LoggedIn;
                updateGUI();
            }
            else
            {
                infoTextLabel.setText(waitingText);
                setGameClient(gc);
                gc.setWebClient(this);

                Timer timeoutStartup = setupTimer();

                while (!clientIsUp && !timeIsUp)
                {
                    synchronized (comingUpMutex)
                    {
                        try
                        {
                            comingUpMutex.wait();
                        }
                        catch (InterruptedException e)
                        {
                            // ignored
                        }
                        catch (Exception e)
                        {
                            // just to be sure...
                        }
                    }
                }
                timeoutStartup.cancel();

                if (clientIsUp)
                {
                    state = Playing;
                    updateGUI();
                    onGameStartAutoAction();
                }
                else
                {
                    JOptionPane
                        .showMessageDialog(
                            this,
                            "Own client could connect, but game did not start "
                            + "(probably some other player connecting failed?)",
                            "Starting game failed!",
                            JOptionPane.ERROR_MESSAGE);
                    state = LoggedIn;
                    updateGUI();
                }
            }
        }
        catch (Exception e)
        {
            // client startup failed for some reason
            JOptionPane.showMessageDialog(this,
                "Unexpected exception while starting the game client: "
                    + e.toString(), "Starting game failed!", 
                    JOptionPane.ERROR_MESSAGE);
            state = LoggedIn;
            updateGUI();
        }
    }

    public void gameCancelled(String gameId, String byUser)
    {
        if (state == Enrolled && enrolledGameId.equals(gameId))
        {
            String message = "Game " + gameId + " was cancelled by user "
                + byUser;
            JOptionPane.showMessageDialog(this, message);
            state = LoggedIn;
            enrolledGameId = "";
            updateGUI();
        }

        potGameDataModel.removeGame(gameId);

    }

    public void chatDeliver(String chatId, long when, String sender,
        String message, boolean resent)
    {
        if (chatId.equals(IWebServer.generalChatName))
        {
            generalChat.chatDeliver(when, sender, message, resent);
        }
        else
        {
            // chat delivery to chat other than general not implemented
        }
    }

    // Game Client tells us this when user closes the masterboard
    public void tellGameEnds()
    {
        state = LoggedIn;
        enrolledGameId = "";
        updateGUI();
    }

    // Server tells us the amount of players in the different states
    public void userInfo(int loggedin, int enrolled, int playing, int dead,
        long ago, String text)
    {
        usersLoggedIn = loggedin;
        usersEnrolled = enrolled;
        usersPlaying = playing;
        usersDead = dead;
        usersLogoffAgo = ago;
        usersText = text;
        updateGUI();
    }

    /*
     * Server tells us news about games in "proposed" state
     * - created ones, enrolled player count changed, or
     *   when it is removed (cancelled or started) 
     * 
     **/
    public void gameInfo(GameInfo gi)
    {
        synchronized (gamesUpdates)
        {
            gamesUpdates.add(gi);
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                synchronized (gamesUpdates)
                {
                    Iterator<GameInfo> it = gamesUpdates.iterator();
                    while (it.hasNext())
                    {
                        GameInfo game = it.next();

                        int state = game.getGameState();
                        if (state == GameInfo.Scheduled)
                        {
                            System.out.println("Got a scheduled game, replacing in sched list");
                            replaceInTable(schedulingPanel.getSchedGameTable(), game);
                        }
                        else if (state == GameInfo.Proposed)
                        {
                            System.out.println("Got a proposed game, replacing in proposed list");
                            replaceInTable(potGameTable, game);
                        }
                        else if (state == GameInfo.Running)
                        {
                            System.out.println("Got a running game, replacing in run game list and remove in pot game list");
                            replaceInTable(runGameTable, game);
                            potGameDataModel.removeGame(game.getGameId());
                        }
                        else if (state == GameInfo.Ending)
                        {
                            runGameDataModel.removeGame(game.getGameId());
                        }
                        else
                        {
                            LOGGER.log(Level.WARNING,
                                "Huups, unhandled game state "
                                    + game.getStateString());
                        }
                    }
                    gamesUpdates.clear();
                }
                updateGUI();
            }
        });

    }

    private void replaceInTable(JTable table, GameInfo gi)
    {
        GameTableModel model = (GameTableModel)table.getModel();

        int index = model.getRowIndex(gi.getGameId()).intValue();
        model.setRowAt(gi, index);
        table.repaint();
    }

    private void resetTable(JTable table)
    {
        GameTableModel model = (GameTableModel)table.getModel();
        model.resetTable();
    }

    public void connectionReset(boolean forced)
    {
        String message = (forced ? "Some other connection to server with same login name forced your logout."
            : "Connection reset by server! You are logged out.");
        JOptionPane.showMessageDialog(this, message);
        setAdmin(false);
        state = NotLoggedIn;
        enrolledGameId = "";
        receivedField.setText("Connection reset by server!");
        updateStatus("Not connected", Color.red);

        loginField.setEnabled(true);
        gameHash.clear();
        synchronized (gamesUpdates)
        {
            gamesUpdates.clear();
        }
        resetTable(potGameTable);
        resetTable(runGameTable);
        updateGUI();

        tabbedPane.setSelectedComponent(serverTab);
    }

    // ========================= Event Handling stuff ======================

    public void actionPerformed(ActionEvent e)
    {
        String command = e.getActionCommand();
        Object source = e.getSource();

        if (source == proposeButton)
        {
            int min = ((Integer)spinner1.getValue()).intValue();
            int target = ((Integer)spinner2.getValue()).intValue();
            int max = ((Integer)spinner3.getValue()).intValue();

            do_proposeGame(variantBox.getSelectedItem().toString(),
                viewmodeBox.getSelectedItem().toString(), eventExpiringBox
                    .getSelectedItem().toString(), unlimitedMulligansCB
                    .isSelected(), balancedTowersCB.isSelected(), min, target,
                max);
        }

        else if (source == enrollButton)
        {
            int selRow = potGameTable.getSelectedRow();
            if (selRow != -1)
            {
                String gameId = (String)potGameTable.getValueAt(selRow, 0);
                boolean ok = doEnroll(gameId);
                if (ok)
                {
                    // potGameTable.setEnabled(false);
                }
            }
        }
        else if (source == unenrollButton)
        {
            int selRow = potGameTable.getSelectedRow();
            if (selRow != -1)
            {
                String gameId = (String)potGameTable.getValueAt(selRow, 0);
                boolean ok = doUnenroll(gameId);
                if (ok)
                {
                    potGameTable.setEnabled(true);
                }
            }
        }

        else if (source == cancelButton)
        {
            int selRow = potGameTable.getSelectedRow();
            if (selRow != -1)
            {
                String gameId = (String)potGameTable.getValueAt(selRow, 0);
                doCancel(gameId);
            }
        }

        else if (source == startButton)
        {
            int selRow = potGameTable.getSelectedRow();
            if (selRow != -1)
            {
                String gameId = (String)potGameTable.getValueAt(selRow, 0);

                boolean ok = doStart(gameId);
                if (ok)
                {
                    potGameTable.setEnabled(false);
                }
            }
        }

        else if (source == hideButton)
        {
            setVisible(false);
        }
        else if (source == quitButton)
        {
            doQuit();
        }
        else if (command.equals(LoginButtonText))
        {
            doLogin();
        }
        else if (command.equals(LogoutButtonText))
        {
            doLogout();
        }
        else if (source == autologinCB)
        {
            options.setOption(AutoLoginCBText, autologinCB.isSelected());
        }
        else if (source == autoGamePaneCB)
        {
            options.setOption(AutoGamePaneCBText, autoGamePaneCB.isSelected());
        }

        else if (source == registerOrPasswordButton)
        {
            // createAccountButtonText chgPasswordButtonText
            if (command.equals(createAccountButtonText))
            {
                doRegisterOrPasswordDialog(true);
            }
            else if (command.equals(chgPasswordButtonText))
            {
                doRegisterOrPasswordDialog(false);
            }
        }

        // development/debug purposes only
        else if (source == debugSubmitButton)
        {
            String text = commandField.getText();
            ((WebClientSocketThread)server).submitAnyText(text);
            commandField.setText("");
        }

        else if (command.equals("Shutdown Server"))
        {
            server.shutdownServer();
        }

        else if (source == autoGSNothingRB || source == autoGSHideRB
            || source == autoGSCloseRB)
        {
            options.setOption(optAutoGameStartAction, command);
        }

        else if (source == variantBox)
        {
            options.setOption(Options.variant, (String)variantBox
                .getSelectedItem());
            // don't care here - we read it when user does the propose action
        }

        else if (source == viewmodeBox)
        {
            options.setOption(Options.viewMode, (String)viewmodeBox
                .getSelectedItem());
        }

        else if (source == eventExpiringBox)
        {
            options.setOption(Options.eventExpiring, (String)eventExpiringBox
                .getSelectedItem());
        }

        else if (source == balancedTowersCB)
        {
            options.setOption(Options.balancedTowers, balancedTowersCB
                .isSelected());
        }

        else if (source == unlimitedMulligansCB)
        {
            options.setOption(Options.unlimitedMulligans, unlimitedMulligansCB
                .isSelected());
        }

        else if (generalChat.submitWasHandled(source))
        {
            // chatHandler did all what is needed; returned true
            // iff it was an event in that chatTab
        }
        else
        // A combo box was changed.
        {
            // Only combo boxes are variant and view mode.
            // We don't react on their change right now;
            // rather, we read the current state then when
            // user presses Propose game button.
        }
    }

    @Override
    public void windowClosing(WindowEvent e)
    {
        Start startObj = Start.getCurrentStartObject();
        startObj.setWhatToDoNext(Start.GetPlayersDialog);
        dispose();
    }

    @Override
    public void mouseClicked(MouseEvent e)
    {
        // nothing to do
    }

    @Override
    public void mouseEntered(MouseEvent e)
    {
        // nothing to do
    }

    @Override
    public void mouseExited(MouseEvent e)
    {
        // nothing to do
    }

    @Override
    public void mousePressed(MouseEvent e)
    {
        // nothing to do
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
        // nothing to do
    }

    @Override
    public void windowClosed(WindowEvent e)
    {
        // nothing to do
    }

    @Override
    public void windowActivated(WindowEvent e)
    {
        // nothing to do
    }

    @Override
    public void windowDeactivated(WindowEvent e)
    {
        // nothing to do
    }

    @Override
    public void windowDeiconified(WindowEvent e)
    {
        // nothing to do
    }

    @Override
    public void windowIconified(WindowEvent e)
    {
        //
    }

    @Override
    public void windowOpened(WindowEvent e)
    {
        // nothing to do
    }

    class GameTableModel extends AbstractTableModel
    {
        private final String[] columnNames = { "#", "state", "by", 
            "when", "duration", "info", 
            "Variant",
            "Viewmode", "Expire", "Mull", "Towers", "min", "target", "max",
            "actual", "players" };

        private final Vector<GameInfo> data = new Vector<GameInfo>(16, 1);
        private final HashMap<String, Integer> rowIndex = new HashMap<String, Integer>();

        public int getColumnCount()
        {
            return columnNames.length;
        }

        public int getRowCount()
        {
            return data.size();
        }

        @Override
        public String getColumnName(int col)
        {
            return columnNames[col];
        }

        public Object getValueAt(int row, int col)
        {
            int rows = data.size();

            if (row >= rows)
            {
                return "-";
            }

            GameInfo gi = data.get(row);
            if (gi == null)
            {
                return "-";
            }
            Object o = null;
            switch (col)
            {
                case 0:
                    o = gi.getGameId();
                    break;

                case 1:
                    o = gi.getStateString();
                    break;

                case 2:
                    o = gi.getInitiator();
                    break;

                case 3:
                    o = humanReadableTime(gi.getStartTime());
                    break;
                    
                case 4:
                    o = gi.getDuration().toString() + " min.";
                    break;

                case 5:
                    o = gi.getSummary();
                    break;

                case 6:
                    o = gi.getVariant();
                    break;

                case 7:
                    o = gi.getViewmode();
                    break;

                case 8:
                    o = gi.getEventExpiring();
                    break;

                case 9:
                    o = Boolean.valueOf(gi.getUnlimitedMulligans());
                    break;

                case 10:
                    o = Boolean.valueOf(gi.getBalancedTowers());
                    break;

                case 11:
                    o = gi.getMin();
                    break;

                case 12:
                    o = gi.getTarget();
                    break;

                case 13:
                    o = gi.getMax();
                    break;

                case 14:
                    o = gi.getEnrolledCount();
                    break;

                case 15:
                    o = gi.getPlayerListAsString();
                    break;
            }
            return o;
        }

        @Override
        public Class<?> getColumnClass(int col)
        {
            Class<?> c = String.class;

            switch (col)
            {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                    c = String.class;
                    break;

                case 9:
                case 10:
                    c = Boolean.class;
                    break;

                case 11:
                case 12:
                case 13:
                case 14:
                    c = Integer.class;
                    break;

                case 15:
                    c = String.class;
                    break;
            }

            return c;
        }

        // TableModel forces us into casting
        @SuppressWarnings("unchecked")
        @Override
        public void setValueAt(Object value, int row, int col)
        {
            if (col == -1)
            {
                setRowAt(value, row);
                return;
            }
            GameInfo gi = data.get(row);
            if (gi == null)
            {
                gi = new GameInfo("");

            }
            switch (col)
            {
                case 0:
                    gi.setGameId((String)value);
                    break;

                /*            case 0: String gameId = (String) value;
                 gi.setGameId(gameId);
                 rowIndex.put(gameId, new Integer(row));
                 break;
                 */
                case 1:
                    gi.setState((Integer)value);
                    break;

                case 2:
                    gi.setInitiator((String)value);
                    break;

                case 3:
                    gi.setStartTime((String)value);
                    break;

                case 4:
                    gi.setDuration((String)value);
                    break;

                case 5:
                    gi.setSummary((String)value);
                    break;
                    
                case 6:
                    gi.setVariant((String)value);
                    break;

                case 7:
                    gi.setViewmode((String)value);
                    break;

                case 8:
                    gi.setEventExpiring((String)value);
                    break;

                case 9:
                    gi.setUnlimitedMulligans(((Boolean)value).booleanValue());
                    break;

                case 10:
                    gi.setUnlimitedMulligans(((Boolean)value).booleanValue());
                    break;

                case 11:
                    gi.setMin((Integer)value);
                    break;

                case 12:
                    gi.setTarget((Integer)value);
                    break;

                case 13:
                    gi.setMax((Integer)value);
                    break;

                case 14:
                    gi.setEnrolledCount((Integer)value);
                    break;

                case 15:
                    gi.setPlayerList((ArrayList<User>)value);
                    break;
            }
            fireTableCellUpdated(row, col);
        }

        public int addGame(GameInfo gi)
        {
            int nextIndex = data.size();
            data.add(gi);
            String gameId = gi.getGameId();
            rowIndex.put(gameId, Integer.valueOf(nextIndex));

            fireTableRowsUpdated(nextIndex, nextIndex);
            return nextIndex;
        }

        public void removeGame(String gameId)
        {
            int index = this.findRowIndex(gameId);
            if (index != -1)
            {
                data.remove(index);
                rowIndex.remove(gameId);
                redoRowIndices();
                fireTableRowsDeleted(index, index);
            }
            else
            {
                // no problem. For example on login client gets told all the 
                // running games and client tries to remove them from pot table
                // but they are not there...
            }
        }

        public void resetTable()
        {
            int size = data.size();
            if (size > 0)
            {
                data.clear();
                rowIndex.clear();
                fireTableRowsDeleted(0, size - 1);
            }
        }

        public void redoRowIndices()
        {
            rowIndex.clear();
            int size = data.size();
            int i = 0;
            while (i < size)
            {
                GameInfo gi = data.get(i);
                String gameId = gi.getGameId();
                rowIndex.put(gameId, Integer.valueOf(i));
                i++;
            }
        }

        public void setRowAt(Object value, int row)
        {
            GameInfo gi = (GameInfo)value;
            String gameId = gi.getGameId();
            rowIndex.put(gameId, Integer.valueOf(row));

            data.set(row, gi);

            fireTableRowsUpdated(row, row);
        }

        public int findRowIndex(String gameId)
        {
            Integer iI = rowIndex.get(gameId);
            if (iI == null)
            {
                return -1;
            }
            else
            {
                return iI.intValue();
            }
        }

        public Integer getRowIndex(String gameId)
        {
            Integer index = rowIndex.get(gameId);
            if (index == null)
            {
                index = Integer.valueOf(data.size());
                int row = index.intValue();
                GameInfo gi = new GameInfo(gameId);
                data.add(gi);
                rowIndex.put(gameId, Integer.valueOf(row));
                fireTableRowsInserted(row, row);
            }
            return index;
        }

        private String humanReadableTime(Long startTime)
        {
            String timeString = "";
            
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                DateFormat.SHORT, myLocale);
            df.setTimeZone(TimeZone.getDefault());
            df.setLenient(false);

            timeString = df.format(startTime);

            return timeString;
        }

    } // END Class GameTableModel

    class GameTableSelectionHandler implements ListSelectionListener
    {
        public void valueChanged(ListSelectionEvent e)
        {
            ListSelectionModel lsm = (ListSelectionModel)e.getSource();
            if (lsm == potGameListSelectionModel)
            {
                updateGUI();
            }

            else if (lsm == potGameListSelectionModel)
            {
                updateGUI();
            }
            
            else if (lsm == schedulingPanel.getSchedGameTable())
            {
                System.out.println("update to scheduled game list selection model");
                updateGUI();
            }

            else
            {
                // 
            }
        }
    } // END Class GameTableSelectionHandler 
}
