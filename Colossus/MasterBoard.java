import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import java.io.*;

/**
 * Class MasterBoard implements the GUI for a Titan masterboard.
 * @version $Id$
 * @author David Ripton
 */

public final class MasterBoard extends JPanel implements MouseListener,
    WindowListener, ItemListener
{
    private static ArrayList hexes = new ArrayList();
    private static LinkedList markers = new LinkedList();

    public static final boolean[][] show =
    {
        {false, false, false, true, true, false, false, false},
        {false, false, true, true, true, true, false, false},
        {false, true, true, true, true, true, true, false},
        {true, true, true, true, true, true, true, true},
        {true, true, true, true, true, true, true, true},
        {true, true, true, true, true, true, true, true},
        {true, true, true, true, true, true, true, true},
        {true, true, true, true, true, true, true, true},
        {true, true, true, true, true, true, true, true},
        {true, true, true, true, true, true, true, true},
        {true, true, true, true, true, true, true, true},
        {true, true, true, true, true, true, true, true},
        {false, true, true, true, true, true, true, false},
        {false, false, true, true, true, true, false, false},
        {false, false, false, true, true, false, false, false}
    };


    private static int scale;
    private static Game game;

    private JFrame masterFrame;
    private JMenuItem mi;
    private JMenuBar menuBar;
    private JMenu fileMenu;
    private JMenu phaseMenu;
    private JMenu optionsMenu;
    private JMenu debugMenu;
    private JPopupMenu popupMenu;

    private JCheckBoxMenuItem miShowStatusScreen;
    private JCheckBoxMenuItem miShowDice;
    private JCheckBoxMenuItem miAllStacksVisible;
    private JCheckBoxMenuItem miAutoRecruit;
    private JCheckBoxMenuItem miAutoPickRecruiter;
    private JCheckBoxMenuItem miAutosave;
    private JCheckBoxMenuItem miAntialias;
    private JCheckBoxMenuItem miChooseMovement;

    /** Last point clicked is needed for popup menus. */
    private Point lastPoint;

    private Container contentPane;

    public static final String newGame = "New game";
    public static final String loadGame = "Load game";
    public static final String saveGame = "Save game";
    public static final String saveGameAs = "Save game as";
    public static final String quitGame = "Quit game";
    public static final String concedeBattle = "Concede battle";
    public static final String saveOptions = "Save options";
    public static final String undoLastSplit = "Undo Last Split";
    public static final String undoAllSplits = "Undo All Splits";
    public static final String withdrawFromGame = "Withdraw from Game";
    public static final String doneWithSplits = "Done with Splits";
    public static final String undoLastMove = "Undo Last Move";
    public static final String undoAllMoves = "Undo All Moves";
    public static final String takeMulligan = "Take Mulligan";
    public static final String doneWithMoves = "Done with Moves";
    public static final String doneWithEngagements = "Done with Engagements";
    public static final String undoLastRecruit = "Undo Last Recruit";
    public static final String undoAllRecruits = "Undo All Recruits";
    public static final String doneWithTurn = "Done with Turn";
    public static final String viewRecruitInfo = "View Recruit Info";
    public static final String viewBattleMap = "View Battle Map";

    private AbstractAction undoLastSplitAction;
    private AbstractAction undoAllSplitsAction;
    private AbstractAction doneWithSplitsAction;
    private AbstractAction undoLastMoveAction;
    private AbstractAction undoAllMovesAction;
    private AbstractAction takeMulliganAction;
    private AbstractAction doneWithMovesAction;
    private AbstractAction doneWithEngagementsAction;
    private AbstractAction undoLastRecruitAction;
    private AbstractAction undoAllRecruitsAction;
    private AbstractAction doneWithTurnAction;
    private AbstractAction withdrawFromGameAction;
    private AbstractAction viewRecruitInfoAction;
    private AbstractAction viewBattleMapAction;
    private AbstractAction newGameAction;
    private AbstractAction loadGameAction;
    private AbstractAction saveGameAction;
    private AbstractAction saveGameAsAction;
    private AbstractAction saveOptionsAction;
    private AbstractAction quitGameAction;


    public MasterBoard(Game game)
    {
        masterFrame = new JFrame("MasterBoard");

        this.game = game;

        contentPane = masterFrame.getContentPane();
        contentPane.setLayout(new BorderLayout());

        scale = findScale();

        setOpaque(true);

        setupIcon();

        setBackground(Color.black);

        masterFrame.addWindowListener(this);
        addMouseListener(this);

        setupActions();
        setupPopupMenu();
        setupTopMenu();

        setupHexes();

        contentPane.add(this, BorderLayout.CENTER);
        masterFrame.pack();

        masterFrame.setVisible(true);
    }


    public void setGame(Game game)
    {
        this.game = game;
    }


    private void setupActions()
    {
        undoLastSplitAction = new AbstractAction(undoLastSplit)
        {
            public void actionPerformed(ActionEvent e)
            {
                Player player = game.getActivePlayer();
                player.undoLastSplit();
                repaint();
            }
        };

        undoAllSplitsAction = new AbstractAction(undoAllSplits)
        {
            public void actionPerformed(ActionEvent e)
            {
                Player player = game.getActivePlayer();
                player.undoAllSplits();
                repaint();
            }
        };

        doneWithSplitsAction = new AbstractAction(doneWithSplits)
        {
            public void actionPerformed(ActionEvent e)
            {
                Player player = game.getActivePlayer();
                if (player.getMaxLegionHeight() > 7)
                {
                    JOptionPane.showMessageDialog(masterFrame,
                        "Must split.");
                }
                else
                {
                    advancePhase();
                }
            }
        };

        undoLastMoveAction = new AbstractAction(undoLastMove)
        {
            public void actionPerformed(ActionEvent e)
            {
                Player player = game.getActivePlayer();
                player.undoLastMove();
                game.highlightUnmovedLegions();
            }
        };

        undoAllMovesAction = new AbstractAction(undoAllMoves)
        {
            public void actionPerformed(ActionEvent e)
            {
                Player player = game.getActivePlayer();
                player.undoAllMoves();
                game.highlightUnmovedLegions();
            }
        };

        takeMulliganAction = new AbstractAction(takeMulligan)
        {
            public void actionPerformed(ActionEvent e)
            {
                Player player = game.getActivePlayer();
                player.takeMulligan();

                // Reroll movement die.  Remove Take Mulligan option
                // if applicable.
                setupPhase();
            }
        };

        doneWithMovesAction = new AbstractAction(doneWithMoves)
        {
            public void actionPerformed(ActionEvent e)
            {
                Player player = game.getActivePlayer();
                // If any legion has a legal non-teleport move, then the
                // player must move at least one legion.
                if (player.legionsMoved() == 0 &&
                    player.countMobileLegions() > 0)
                {
                    game.highlightUnmovedLegions();
                    JOptionPane.showMessageDialog(masterFrame,
                        "At least one legion must move.");
                }
                else
                {
                    // If legions share a hex and have a legal non-teleport
                    // move, force one of them to take it.
                    if (player.splitLegionHasForcedMove())
                    {
                        game.highlightUnmovedLegions();
                        JOptionPane.showMessageDialog(masterFrame,
                            "Split legions must be separated.");
                    }
                    // Otherwise, recombine all split legions still in the
                    // same hex, and move on to the next phase.
                    else
                    {
                        player.undoAllSplits();
                        advancePhase();
                    }
                }
            }
        };

        doneWithEngagementsAction = new AbstractAction(doneWithEngagements)
        {
            public void actionPerformed(ActionEvent e)
            {
                // Advance only if there are no unresolved engagements.
                if (game.highlightEngagements() == 0)
                {
                    advancePhase();
                }
                else
                {
                    JOptionPane.showMessageDialog(masterFrame,
                        "Must Resolve Engagements.");
                }
            }
        };

        undoLastRecruitAction = new AbstractAction(undoLastRecruit)
        {
            public void actionPerformed(ActionEvent e)
            {
                Player player = game.getActivePlayer();
                player.undoLastRecruit();
                game.highlightPossibleRecruits();
            }
        };

        undoAllRecruitsAction = new AbstractAction(undoAllRecruits)
        {
            public void actionPerformed(ActionEvent e)
            {
                Player player = game.getActivePlayer();
                player.undoAllRecruits();
                game.highlightPossibleRecruits();
            }
        };

        doneWithTurnAction = new AbstractAction(doneWithTurn)
        {
            public void actionPerformed(ActionEvent e)
            {
                Player player = game.getActivePlayer();
                player.commitMoves();
                // Mulligans are only allowed on turn 1.
                player.setMulligansLeft(0);
                advancePhase();
            }
        };

        withdrawFromGameAction = new AbstractAction(withdrawFromGame)
        {
            public void actionPerformed(ActionEvent e)
            {
                Player player = game.getActivePlayer();
                String [] options = new String[2];
                options[0] = "Yes";
                options[1] = "No";
                int answer = JOptionPane.showOptionDialog(masterFrame,
                    "Are you sure you with to withdraw from the game?",
                    "Confirm Withdrawal?",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[1]);

                if (answer == JOptionPane.YES_OPTION)
                {
                   player.die(null, true);
                   advancePhase();
                }
            }
        };

        viewRecruitInfoAction = new AbstractAction(viewRecruitInfo)
        {
            public void actionPerformed(ActionEvent e)
            {
                MasterHex hex = getHexContainingPoint(lastPoint);
                if (hex != null)
                {
                    new ShowMasterHex(masterFrame, hex, lastPoint);
                    // Work around a Windows JDK 1.3 bug.
                    hex.repaint();
                }
            }
        };

        viewBattleMapAction = new AbstractAction(viewBattleMap)
        {
            public void actionPerformed(ActionEvent e)
            {
                MasterHex hex = getHexContainingPoint(lastPoint);
                if (hex != null)
                {
                    new ShowBattleMap(masterFrame, hex);
                    // Work around a Windows JDK 1.3 bug.
                    hex.repaint();
                }
            }
        };

        newGameAction = new AbstractAction(newGame)
        {
            public void actionPerformed(ActionEvent e)
            {
                String [] options = new String[2];
                options[0] = "Yes";
                options[1] = "No";
                int answer = JOptionPane.showOptionDialog(masterFrame,
                    "Are you sure you with to start a new game?",
                    "New Game?",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[1]);

                if (answer == JOptionPane.YES_OPTION)
                {
                    game.newGame();
                }
            }
        };

        loadGameAction = new AbstractAction(loadGame)
        {
            public void actionPerformed(ActionEvent e)
            {
                String [] options = new String[2];
                options[0] = "Yes";
                options[1] = "No";
                int answer = JOptionPane.showOptionDialog(masterFrame,
                    "Are you sure you with to load another game?",
                    "Load Game?",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[1]);

                if (answer == JOptionPane.YES_OPTION)
                {
                    JFileChooser chooser = new JFileChooser(Game.saveDirname);
                    chooser.setFileFilter(new SaveGameFilter());
                    int returnVal = chooser.showOpenDialog(masterFrame);
                    if (returnVal == JFileChooser.APPROVE_OPTION)
                    {
                        game.loadGame(chooser.getSelectedFile().getName());
                    }
                }
            }
        };

        saveGameAction = new AbstractAction(saveGame)
        {
            public void actionPerformed(ActionEvent e)
            {
                // XXX Offer the ability to re-use a save filename,
                // rather than always making a new one?
                game.saveGame();
            }
        };

        saveGameAsAction = new AbstractAction(saveGameAs)
        {
            // XXX Need a confirmation dialog on overwrite?
            public void actionPerformed(ActionEvent e)
            {
                JFileChooser chooser = new JFileChooser(Game.saveDirname);
                chooser.setFileFilter(new SaveGameFilter());
                int returnVal = chooser.showSaveDialog(masterFrame);
                if (returnVal == JFileChooser.APPROVE_OPTION)
                {
                    String dirname = chooser.getCurrentDirectory().getName();
                    String basename = chooser.getSelectedFile().getName();
                    StringBuffer path = new StringBuffer();
                    path.append(dirname);
                    path.append(File.separator);
                    path.append(basename);
                    // Add default savegame extension.
                    if (!path.toString().endsWith(Game.saveExtension))
                    {
                        path.append(Game.saveExtension);
                    }
                    game.saveGame(path.toString());
                }
            }
        };

        saveOptionsAction = new AbstractAction(saveOptions)
        {
            public void actionPerformed(ActionEvent e)
            {
                game.saveOptions();
            }
        };

        quitGameAction = new AbstractAction(quitGame)
        {
            public void actionPerformed(ActionEvent e)
            {
                String [] options = new String[2];
                options[0] = "Yes";
                options[1] = "No";
                int answer = JOptionPane.showOptionDialog(masterFrame,
                    "Are you sure you with to quit?",
                    "Quit Game?",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[1]);

                if (answer == JOptionPane.YES_OPTION)
                {
                    game.dispose();
                }
            }
        };

        // If running as an applet, disable all file-related actions.
        if (game != null && game.isApplet())
        {
            newGameAction.setEnabled(false);
            loadGameAction.setEnabled(false);
            saveGameAction.setEnabled(false);
            saveGameAsAction.setEnabled(false);
            saveOptionsAction.setEnabled(false);
        }
    }


    private void setupPopupMenu()
    {
        popupMenu = new JPopupMenu();
        contentPane.add(popupMenu);

        mi = popupMenu.add(viewRecruitInfoAction);
        mi.setMnemonic(KeyEvent.VK_R);

        mi = popupMenu.add(viewBattleMapAction);
        mi.setMnemonic(KeyEvent.VK_B);
    }


    private void setupTopMenu()
    {
        menuBar = new JMenuBar();
        masterFrame.setJMenuBar(menuBar);

        // File menu

        fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        menuBar.add(fileMenu);
        mi = fileMenu.add(newGameAction);
        mi.setMnemonic(KeyEvent.VK_N);
        mi = fileMenu.add(loadGameAction);
        mi.setMnemonic(KeyEvent.VK_L);
        mi = fileMenu.add(saveGameAction);
        mi.setMnemonic(KeyEvent.VK_S);
        mi = fileMenu.add(saveGameAsAction);
        mi.setMnemonic(KeyEvent.VK_A);
        mi = fileMenu.add(quitGameAction);
        mi.setMnemonic(KeyEvent.VK_Q);

        // Phase menu items change by phase and will be set up later.
        phaseMenu = new JMenu("Phase");
        phaseMenu.setMnemonic(KeyEvent.VK_P);
        menuBar.add(phaseMenu);

        optionsMenu = new JMenu("Options");
        optionsMenu.setMnemonic(KeyEvent.VK_O);
        menuBar.add(optionsMenu);

        // Game-wide options first

        miAutosave = new JCheckBoxMenuItem(Game.sAutosave);
        miAutosave.setMnemonic(KeyEvent.VK_A);
        // Do not allow autosave if running as an applet.
        if (game.isApplet())
        {
            game.setAutosave(false);
            miAutosave.setEnabled(false);
        }
        miAutosave.setSelected(game.getAutosave());
        miAutosave.addItemListener(this);
        optionsMenu.add(miAutosave);

        miAllStacksVisible = new JCheckBoxMenuItem(Game.sAllStacksVisible);
        miAllStacksVisible.setMnemonic(KeyEvent.VK_S);
        miAllStacksVisible.setSelected(game.getAllStacksVisible());
        miAllStacksVisible.addItemListener(this);
        optionsMenu.add(miAllStacksVisible);

        optionsMenu.addSeparator();

        // Then per-player options

        miAutoRecruit = new JCheckBoxMenuItem(Game.sAutoRecruit);
        miAutoRecruit.setMnemonic(KeyEvent.VK_R);
        miAutoRecruit.setSelected(game.getAutoRecruit());
        miAutoRecruit.addItemListener(this);
        optionsMenu.add(miAutoRecruit);

        miAutoPickRecruiter = new JCheckBoxMenuItem(Game.sAutoPickRecruiter);
        miAutoPickRecruiter.setMnemonic(KeyEvent.VK_P);
        miAutoPickRecruiter.setSelected(game.getAutoPickRecruiter());
        miAutoPickRecruiter.addItemListener(this);
        optionsMenu.add(miAutoPickRecruiter);

        optionsMenu.addSeparator();

        // Then per-client GUI options

        miShowStatusScreen = new JCheckBoxMenuItem(Game.sShowStatusScreen);
        miShowStatusScreen.setMnemonic(KeyEvent.VK_G);
        miShowStatusScreen.setSelected(game.getShowStatusScreen());
        miShowStatusScreen.addItemListener(this);
        optionsMenu.add(miShowStatusScreen);

        miShowDice = new JCheckBoxMenuItem(Game.sShowDice);
        miShowDice.setMnemonic(KeyEvent.VK_D);
        miShowDice.setSelected(game.getShowDice());
        miShowDice.addItemListener(this);
        optionsMenu.add(miShowDice);

        miAntialias = new JCheckBoxMenuItem(Game.sAntialias);
        miAntialias.setMnemonic(KeyEvent.VK_N);
        miAntialias.setSelected(game.getAntialias());
        miAntialias.addItemListener(this);
        optionsMenu.add(miAntialias);

        optionsMenu.addSeparator();

        mi = optionsMenu.add(saveOptionsAction);
        mi.setMnemonic(KeyEvent.VK_O);
        
        // Debug menu 

        debugMenu = new JMenu("Debug");
        debugMenu.setMnemonic(KeyEvent.VK_D);
        menuBar.add(debugMenu);
        
        miChooseMovement = new JCheckBoxMenuItem(Game.sChooseMovement);
        miChooseMovement.setMnemonic(KeyEvent.VK_M);
        miChooseMovement.setSelected(game.getChooseMovement());
        miChooseMovement.addItemListener(this);
        debugMenu.add(miChooseMovement);

        // Force number of hits
        // Allow changing creature types
    }


    public void twiddleAutosave(boolean enable)
    {
        // Do not allow autosave if running as an applet.
        if (game.isApplet())
        {
            enable = false;
        }
        miAutosave.setSelected(enable);
    }


    public void twiddleAllStacksVisible(boolean enable)
    {
        miAllStacksVisible.setSelected(enable);
    }


    public void twiddleAutoRecruit(boolean enable)
    {
        miAutoRecruit.setSelected(enable);
    }


    public void twiddleAutoPickRecruiter(boolean enable)
    {
        miAutoPickRecruiter.setSelected(enable);
    }


    public void twiddleShowStatusScreen(boolean enable)
    {
        miShowStatusScreen.setSelected(enable);
    }


    public void twiddleShowDice(boolean enable)
    {
        miShowDice.setSelected(enable);
    }


    public void twiddleAntialias(boolean enable)
    {
        miAntialias.setSelected(enable);
    }


    private void setupHexes()
    {
        // There are a total of 96 hexes
        // Their Titan labels are:
        // Middle ring: 1-42
        // Outer ring: 101-142
        // Towers: 100, 200, 300, 400, 500, 600
        // Inner ring: 1000, 2000, 3000, 4000, 5000, 6000

        // For easy of mapping to the GUI, they'll be stored
        // in a 15x8 array, with some empty elements.

        MasterHex[][] h = new MasterHex[15][8];

        int cx = 3 * scale;
        int cy = 0 * scale;

        // Initialize hexes.
        for (int i = 0; i < h.length; i++)
        {
            for (int j = 0; j < h[0].length; j++)
            {
                if (show[i][j])
                {
                    MasterHex hex = new MasterHex(cx + 4 * i * scale,
                        (int) Math.round(cy + (3 * j + (i & 1) *
                        (1 + 2 * (j >> 1)) + ((i + 1) & 1) * 2 *
                        ((j + 1) >> 1)) * Hex.SQRT3 * scale), scale,
                        ((i + j) & 1) == 0, this);

                    hex.setXCoord(i);
                    hex.setYCoord(j);

                    h[i][j] = hex;
                    hexes.add(hex);
                }
            }
        }



        // Add terrain types, id labels, label sides, and exits to hexes.
        h[0][3].setTerrain('S');
        h[0][3].setLabel(132);
        h[0][3].setLabelSide(2);
        h[0][3].setExitType(1, MasterHex.ARROWS);

        h[0][4].setTerrain('P');
        h[0][4].setLabel(133);
        h[0][4].setLabelSide(1);
        h[0][4].setExitType(0, MasterHex.ARROWS);

        h[1][2].setTerrain('B');
        h[1][2].setLabel(130);
        h[1][2].setLabelSide(2);
        h[1][2].setExitType(1, MasterHex.ARROWS);

        h[1][3].setTerrain('M');
        h[1][3].setLabel(131);
        h[1][3].setLabelSide(5);
        h[1][3].setExitType(0, MasterHex.ARROWS);
        h[1][3].setExitType(2, MasterHex.ARCH);

        h[1][4].setTerrain('B');
        h[1][4].setLabel(134);
        h[1][4].setLabelSide(4);
        h[1][4].setExitType(1, MasterHex.ARCH);
        h[1][4].setExitType(5, MasterHex.ARROWS);

        h[1][5].setTerrain('J');
        h[1][5].setLabel(135);
        h[1][5].setLabelSide(1);
        h[1][5].setExitType(0, MasterHex.ARROWS);

        h[2][1].setTerrain('D');
        h[2][1].setLabel(128);
        h[2][1].setLabelSide(2);
        h[2][1].setExitType(1, MasterHex.ARROWS);

        h[2][2].setTerrain('P');
        h[2][2].setLabel(129);
        h[2][2].setLabelSide(5);
        h[2][2].setExitType(0, MasterHex.ARROWS);
        h[2][2].setExitType(2, MasterHex.ARCH);

        h[2][3].setTerrain('H');
        h[2][3].setLabel(32);
        h[2][3].setLabelSide(2);
        h[2][3].setExitType(3, MasterHex.ARROWS);
        h[2][3].setExitType(5, MasterHex.BLOCK);

        h[2][4].setTerrain('J');
        h[2][4].setLabel(33);
        h[2][4].setLabelSide(1);
        h[2][4].setExitType(2, MasterHex.ARROWS);
        h[2][4].setExitType(4, MasterHex.BLOCK);

        h[2][5].setTerrain('M');
        h[2][5].setLabel(136);
        h[2][5].setLabelSide(4);
        h[2][5].setExitType(1, MasterHex.ARCH);
        h[2][5].setExitType(5, MasterHex.ARROWS);

        h[2][6].setTerrain('B');
        h[2][6].setLabel(137);
        h[2][6].setLabelSide(1);
        h[2][6].setExitType(0, MasterHex.ARROWS);

        h[3][0].setTerrain('M');
        h[3][0].setLabel(126);
        h[3][0].setLabelSide(2);
        h[3][0].setExitType(1, MasterHex.ARROWS);

        h[3][1].setTerrain('B');
        h[3][1].setLabel(127);
        h[3][1].setLabelSide(5);
        h[3][1].setExitType(0, MasterHex.ARROWS);
        h[3][1].setExitType(2, MasterHex.ARCH);

        h[3][2].setTerrain('T');
        h[3][2].setLabel(500);
        h[3][2].setLabelSide(2);
        h[3][2].setExitType(1, MasterHex.ARROW);
        h[3][2].setExitType(3, MasterHex.ARROW);
        h[3][2].setExitType(5, MasterHex.ARROW);

        h[3][3].setTerrain('B');
        h[3][3].setLabel(31);
        h[3][3].setLabelSide(5);
        h[3][3].setExitType(0, MasterHex.ARCH);
        h[3][3].setExitType(4, MasterHex.ARROWS);

        h[3][4].setTerrain('P');
        h[3][4].setLabel(34);
        h[3][4].setLabelSide(4);
        h[3][4].setExitType(1, MasterHex.ARROWS);
        h[3][4].setExitType(3, MasterHex.ARCH);

        h[3][5].setTerrain('T');
        h[3][5].setLabel(600);
        h[3][5].setLabelSide(1);
        h[3][5].setExitType(0, MasterHex.ARROW);
        h[3][5].setExitType(2, MasterHex.ARROW);
        h[3][5].setExitType(4, MasterHex.ARROW);

        h[3][6].setTerrain('P');
        h[3][6].setLabel(138);
        h[3][6].setLabelSide(4);
        h[3][6].setExitType(1, MasterHex.ARCH);
        h[3][6].setExitType(5, MasterHex.ARROWS);

        h[3][7].setTerrain('D');
        h[3][7].setLabel(139);
        h[3][7].setLabelSide(1);
        h[3][7].setExitType(0, MasterHex.ARROWS);

        h[4][0].setTerrain('J');
        h[4][0].setLabel(125);
        h[4][0].setLabelSide(3);
        h[4][0].setExitType(2, MasterHex.ARROWS);

        h[4][1].setTerrain('J');
        h[4][1].setLabel(26);
        h[4][1].setLabelSide(2);
        h[4][1].setExitType(3, MasterHex.ARROWS);
        h[4][1].setExitType(5, MasterHex.BLOCK);

        h[4][2].setTerrain('M');
        h[4][2].setLabel(27);
        h[4][2].setLabelSide(5);
        h[4][2].setExitType(2, MasterHex.ARROWS);
        h[4][2].setExitType(4, MasterHex.ARCH);

        h[4][3].setTerrain('W');
        h[4][3].setLabel(30);
        h[4][3].setLabelSide(2);
        h[4][3].setExitType(3, MasterHex.ARCH);
        h[4][3].setExitType(5, MasterHex.ARROWS);

        h[4][4].setTerrain('D');
        h[4][4].setLabel(35);
        h[4][4].setLabelSide(1);
        h[4][4].setExitType(0, MasterHex.ARCH);
        h[4][4].setExitType(2, MasterHex.ARROWS);

        h[4][5].setTerrain('B');
        h[4][5].setLabel(38);
        h[4][5].setLabelSide(4);
        h[4][5].setExitType(3, MasterHex.ARROWS);
        h[4][5].setExitType(5, MasterHex.ARCH);

        h[4][6].setTerrain('W');
        h[4][6].setLabel(39);
        h[4][6].setLabelSide(1);
        h[4][6].setExitType(2, MasterHex.ARROWS);
        h[4][6].setExitType(4, MasterHex.BLOCK);

        h[4][7].setTerrain('M');
        h[4][7].setLabel(140);
        h[4][7].setLabelSide(0);
        h[4][7].setExitType(5, MasterHex.ARROWS);

        h[5][0].setTerrain('P');
        h[5][0].setLabel(124);
        h[5][0].setLabelSide(0);
        h[5][0].setExitType(1, MasterHex.ARROWS);
        h[5][0].setExitType(3, MasterHex.ARCH);

        h[5][1].setTerrain('W');
        h[5][1].setLabel(25);
        h[5][1].setLabelSide(3);
        h[5][1].setExitType(0, MasterHex.BLOCK);
        h[5][1].setExitType(4, MasterHex.ARROWS);

        h[5][2].setTerrain('S');
        h[5][2].setLabel(28);
        h[5][2].setLabelSide(2);
        h[5][2].setExitType(1, MasterHex.ARCH);
        h[5][2].setExitType(3, MasterHex.ARROWS);

        h[5][3].setTerrain('P');
        h[5][3].setLabel(29);
        h[5][3].setLabelSide(5);
        h[5][3].setExitType(2, MasterHex.ARCH);
        h[5][3].setExitType(4, MasterHex.ARROWS);

        h[5][4].setTerrain('M');
        h[5][4].setLabel(36);
        h[5][4].setLabelSide(4);
        h[5][4].setExitType(1, MasterHex.ARCH);
        h[5][4].setExitType(3, MasterHex.ARROWS);

        h[5][5].setTerrain('H');
        h[5][5].setLabel(37);
        h[5][5].setLabelSide(1);
        h[5][5].setExitType(2, MasterHex.ARCH);
        h[5][5].setExitType(4, MasterHex.ARROWS);

        h[5][6].setTerrain('J');
        h[5][6].setLabel(40);
        h[5][6].setLabelSide(0);
        h[5][6].setExitType(1, MasterHex.ARROWS);
        h[5][6].setExitType(3, MasterHex.BLOCK);

        h[5][7].setTerrain('B');
        h[5][7].setLabel(141);
        h[5][7].setLabelSide(3);
        h[5][7].setExitType(0, MasterHex.ARCH);
        h[5][7].setExitType(4, MasterHex.ARROWS);

        h[6][0].setTerrain('B');
        h[6][0].setLabel(123);
        h[6][0].setLabelSide(3);
        h[6][0].setExitType(2, MasterHex.ARROWS);

        h[6][1].setTerrain('B');
        h[6][1].setLabel(24);
        h[6][1].setLabelSide(0);
        h[6][1].setExitType(1, MasterHex.ARCH);
        h[6][1].setExitType(5, MasterHex.ARROWS);

        h[6][2].setTerrain('H');
        h[6][2].setLabel(23);
        h[6][2].setLabelSide(3);
        h[6][2].setExitType(0, MasterHex.ARROWS);
        h[6][2].setExitType(4, MasterHex.ARCH);

        h[6][3].setTerrain('m');
        h[6][3].setLabel(5000);
        h[6][3].setLabelSide(2);
        h[6][3].setExitType(1, MasterHex.ARROW);
        h[6][3].setExitType(3, MasterHex.ARROW);
        h[6][3].setExitType(5, MasterHex.BLOCK);

        h[6][4].setTerrain('t');
        h[6][4].setLabel(6000);
        h[6][4].setLabelSide(1);
        h[6][4].setExitType(0, MasterHex.ARROW);
        h[6][4].setExitType(2, MasterHex.ARROW);
        h[6][4].setExitType(4, MasterHex.BLOCK);

        h[6][5].setTerrain('S');
        h[6][5].setLabel(42);
        h[6][5].setLabelSide(0);
        h[6][5].setExitType(1, MasterHex.ARROWS);
        h[6][5].setExitType(5, MasterHex.ARCH);

        h[6][6].setTerrain('M');
        h[6][6].setLabel(41);
        h[6][6].setLabelSide(3);
        h[6][6].setExitType(0, MasterHex.ARROWS);
        h[6][6].setExitType(2, MasterHex.ARCH);

        h[6][7].setTerrain('S');
        h[6][7].setLabel(142);
        h[6][7].setLabelSide(0);
        h[6][7].setExitType(5, MasterHex.ARROWS);

        h[7][0].setTerrain('M');
        h[7][0].setLabel(122);
        h[7][0].setLabelSide(0);
        h[7][0].setExitType(1, MasterHex.ARROWS);
        h[7][0].setExitType(3, MasterHex.ARCH);

        h[7][1].setTerrain('T');
        h[7][1].setLabel(400);
        h[7][1].setLabelSide(3);
        h[7][1].setExitType(0, MasterHex.ARROW);
        h[7][1].setExitType(2, MasterHex.ARROW);
        h[7][1].setExitType(4, MasterHex.ARROW);

        h[7][2].setTerrain('M');
        h[7][2].setLabel(22);
        h[7][2].setLabelSide(0);
        h[7][2].setExitType(3, MasterHex.ARCH);
        h[7][2].setExitType(5, MasterHex.ARROWS);

        h[7][3].setTerrain('t');
        h[7][3].setLabel(4000);
        h[7][3].setLabelSide(3);
        h[7][3].setExitType(0, MasterHex.BLOCK);
        h[7][3].setExitType(2, MasterHex.ARROW);
        h[7][3].setExitType(4, MasterHex.ARROW);

        h[7][4].setTerrain('m');
        h[7][4].setLabel(1000);
        h[7][4].setLabelSide(0);
        h[7][4].setExitType(1, MasterHex.ARROW);
        h[7][4].setExitType(3, MasterHex.BLOCK);
        h[7][4].setExitType(5, MasterHex.ARROW);

        h[7][5].setTerrain('P');
        h[7][5].setLabel(1);
        h[7][5].setLabelSide(3);
        h[7][5].setExitType(0, MasterHex.ARCH);
        h[7][5].setExitType(2, MasterHex.ARROWS);

        h[7][6].setTerrain('T');
        h[7][6].setLabel(100);
        h[7][6].setLabelSide(0);
        h[7][6].setExitType(1, MasterHex.ARROW);
        h[7][6].setExitType(3, MasterHex.ARROW);
        h[7][6].setExitType(5, MasterHex.ARROW);

        h[7][7].setTerrain('P');
        h[7][7].setLabel(101);
        h[7][7].setLabelSide(3);
        h[7][7].setExitType(0, MasterHex.ARCH);
        h[7][7].setExitType(4, MasterHex.ARROWS);

        h[8][0].setTerrain('S');
        h[8][0].setLabel(121);
        h[8][0].setLabelSide(3);
        h[8][0].setExitType(2, MasterHex.ARROWS);

        h[8][1].setTerrain('P');
        h[8][1].setLabel(20);
        h[8][1].setLabelSide(0);
        h[8][1].setExitType(3, MasterHex.ARROWS);
        h[8][1].setExitType(5, MasterHex.ARCH);

        h[8][2].setTerrain('D');
        h[8][2].setLabel(21);
        h[8][2].setLabelSide(3);
        h[8][2].setExitType(2, MasterHex.ARCH);
        h[8][2].setExitType(4, MasterHex.ARROWS);

        h[8][3].setTerrain('m');
        h[8][3].setLabel(3000);
        h[8][3].setLabelSide(4);
        h[8][3].setExitType(1, MasterHex.BLOCK);
        h[8][3].setExitType(3, MasterHex.ARROW);
        h[8][3].setExitType(5, MasterHex.ARROW);

        h[8][4].setTerrain('t');
        h[8][4].setLabel(2000);
        h[8][4].setLabelSide(5);
        h[8][4].setExitType(0, MasterHex.ARROW);
        h[8][4].setExitType(2, MasterHex.BLOCK);
        h[8][4].setExitType(4, MasterHex.ARROW);

        h[8][5].setTerrain('W');
        h[8][5].setLabel(2);
        h[8][5].setLabelSide(0);
        h[8][5].setExitType(1, MasterHex.ARCH);
        h[8][5].setExitType(3, MasterHex.ARROWS);

        h[8][6].setTerrain('B');
        h[8][6].setLabel(3);
        h[8][6].setLabelSide(3);
        h[8][6].setExitType(2, MasterHex.ARROWS);
        h[8][6].setExitType(4, MasterHex.ARCH);

        h[8][7].setTerrain('B');
        h[8][7].setLabel(102);
        h[8][7].setLabelSide(0);
        h[8][7].setExitType(5, MasterHex.ARROWS);

        h[9][0].setTerrain('B');
        h[9][0].setLabel(120);
        h[9][0].setLabelSide(0);
        h[9][0].setExitType(1, MasterHex.ARROWS);
        h[9][0].setExitType(3, MasterHex.ARCH);

        h[9][1].setTerrain('J');
        h[9][1].setLabel(19);
        h[9][1].setLabelSide(3);
        h[9][1].setExitType(0, MasterHex.BLOCK);
        h[9][1].setExitType(4, MasterHex.ARROWS);

        h[9][2].setTerrain('W');
        h[9][2].setLabel(16);
        h[9][2].setLabelSide(4);
        h[9][2].setExitType(1, MasterHex.ARROWS);
        h[9][2].setExitType(5, MasterHex.ARCH);

        h[9][3].setTerrain('P');
        h[9][3].setLabel(15);
        h[9][3].setLabelSide(1);
        h[9][3].setExitType(0, MasterHex.ARROWS);
        h[9][3].setExitType(4, MasterHex.ARCH);

        h[9][4].setTerrain('M');
        h[9][4].setLabel(8);
        h[9][4].setLabelSide(2);
        h[9][4].setExitType(1, MasterHex.ARROWS);
        h[9][4].setExitType(5, MasterHex.ARCH);

        h[9][5].setTerrain('D');
        h[9][5].setLabel(7);
        h[9][5].setLabelSide(5);
        h[9][5].setExitType(0, MasterHex.ARROWS);
        h[9][5].setExitType(4, MasterHex.ARCH);

        h[9][6].setTerrain('H');
        h[9][6].setLabel(4);
        h[9][6].setLabelSide(0);
        h[9][6].setExitType(1, MasterHex.ARROWS);
        h[9][6].setExitType(3, MasterHex.BLOCK);

        h[9][7].setTerrain('M');
        h[9][7].setLabel(103);
        h[9][7].setLabelSide(3);
        h[9][7].setExitType(0, MasterHex.ARCH);
        h[9][7].setExitType(4, MasterHex.ARROWS);

        h[10][0].setTerrain('P');
        h[10][0].setLabel(119);
        h[10][0].setLabelSide(3);
        h[10][0].setExitType(2, MasterHex.ARROWS);

        h[10][1].setTerrain('H');
        h[10][1].setLabel(18);
        h[10][1].setLabelSide(4);
        h[10][1].setExitType(1, MasterHex.BLOCK);
        h[10][1].setExitType(5, MasterHex.ARROWS);

        h[10][2].setTerrain('B');
        h[10][2].setLabel(17);
        h[10][2].setLabelSide(1);
        h[10][2].setExitType(0, MasterHex.ARROWS);
        h[10][2].setExitType(2, MasterHex.ARCH);

        h[10][3].setTerrain('S');
        h[10][3].setLabel(14);
        h[10][3].setLabelSide(4);
        h[10][3].setExitType(3, MasterHex.ARCH);
        h[10][3].setExitType(5, MasterHex.ARROWS);

        h[10][4].setTerrain('H');
        h[10][4].setLabel(9);
        h[10][4].setLabelSide(5);
        h[10][4].setExitType(0, MasterHex.ARCH);
        h[10][4].setExitType(2, MasterHex.ARROWS);

        h[10][5].setTerrain('P');
        h[10][5].setLabel(6);
        h[10][5].setLabelSide(2);
        h[10][5].setExitType(1, MasterHex.ARCH);
        h[10][5].setExitType(5, MasterHex.ARROWS);

        h[10][6].setTerrain('J');
        h[10][6].setLabel(5);
        h[10][6].setLabelSide(5);
        h[10][6].setExitType(0, MasterHex.ARROWS);
        h[10][6].setExitType(2, MasterHex.BLOCK);

        h[10][7].setTerrain('J');
        h[10][7].setLabel(104);
        h[10][7].setLabelSide(0);
        h[10][7].setExitType(5, MasterHex.ARROWS);

        h[11][0].setTerrain('D');
        h[11][0].setLabel(118);
        h[11][0].setLabelSide(4);
        h[11][0].setExitType(3, MasterHex.ARROWS);

        h[11][1].setTerrain('M');
        h[11][1].setLabel(117);
        h[11][1].setLabelSide(1);
        h[11][1].setExitType(2, MasterHex.ARROWS);
        h[11][1].setExitType(4, MasterHex.ARCH);

        h[11][2].setTerrain('T');
        h[11][2].setLabel(300);
        h[11][2].setLabelSide(4);
        h[11][2].setExitType(1, MasterHex.ARROW);
        h[11][2].setExitType(3, MasterHex.ARROW);
        h[11][2].setExitType(5, MasterHex.ARROW);

        h[11][3].setTerrain('M');
        h[11][3].setLabel(13);
        h[11][3].setLabelSide(1);
        h[11][3].setExitType(0, MasterHex.ARCH);
        h[11][3].setExitType(4, MasterHex.ARROWS);

        h[11][4].setTerrain('B');
        h[11][4].setLabel(10);
        h[11][4].setLabelSide(2);
        h[11][4].setExitType(1, MasterHex.ARROWS);
        h[11][4].setExitType(3, MasterHex.ARCH);

        h[11][5].setTerrain('T');
        h[11][5].setLabel(200);
        h[11][5].setLabelSide(5);
        h[11][5].setExitType(0, MasterHex.ARROW);
        h[11][5].setExitType(2, MasterHex.ARROW);
        h[11][5].setExitType(4, MasterHex.ARROW);

        h[11][6].setTerrain('B');
        h[11][6].setLabel(106);
        h[11][6].setLabelSide(2);
        h[11][6].setExitType(3, MasterHex.ARROWS);
        h[11][6].setExitType(5, MasterHex.ARCH);

        h[11][7].setTerrain('P');
        h[11][7].setLabel(105);
        h[11][7].setLabelSide(5);
        h[11][7].setExitType(4, MasterHex.ARROWS);

        h[12][1].setTerrain('B');
        h[12][1].setLabel(116);
        h[12][1].setLabelSide(4);
        h[12][1].setExitType(3, MasterHex.ARROWS);

        h[12][2].setTerrain('P');
        h[12][2].setLabel(115);
        h[12][2].setLabelSide(1);
        h[12][2].setExitType(2, MasterHex.ARROWS);
        h[12][2].setExitType(4, MasterHex.ARCH);

        h[12][3].setTerrain('J');
        h[12][3].setLabel(12);
        h[12][3].setLabelSide(4);
        h[12][3].setExitType(1, MasterHex.BLOCK);
        h[12][3].setExitType(5, MasterHex.ARROWS);

        h[12][4].setTerrain('W');
        h[12][4].setLabel(11);
        h[12][4].setLabelSide(5);
        h[12][4].setExitType(0, MasterHex.ARROWS);
        h[12][4].setExitType(2, MasterHex.BLOCK);

        h[12][5].setTerrain('M');
        h[12][5].setLabel(108);
        h[12][5].setLabelSide(2);
        h[12][5].setExitType(3, MasterHex.ARROWS);
        h[12][5].setExitType(5, MasterHex.ARCH);

        h[12][6].setTerrain('D');
        h[12][6].setLabel(107);
        h[12][6].setLabelSide(5);
        h[12][6].setExitType(4, MasterHex.ARROWS);

        h[13][2].setTerrain('J');
        h[13][2].setLabel(114);
        h[13][2].setLabelSide(4);
        h[13][2].setExitType(3, MasterHex.ARROWS);

        h[13][3].setTerrain('B');
        h[13][3].setLabel(113);
        h[13][3].setLabelSide(1);
        h[13][3].setExitType(2, MasterHex.ARROWS);
        h[13][3].setExitType(4, MasterHex.ARCH);

        h[13][4].setTerrain('P');
        h[13][4].setLabel(110);
        h[13][4].setLabelSide(2);
        h[13][4].setExitType(3, MasterHex.ARROWS);
        h[13][4].setExitType(5, MasterHex.ARCH);

        h[13][5].setTerrain('B');
        h[13][5].setLabel(109);
        h[13][5].setLabelSide(5);
        h[13][5].setExitType(4, MasterHex.ARROWS);

        h[14][3].setTerrain('M');
        h[14][3].setLabel(112);
        h[14][3].setLabelSide(4);
        h[14][3].setExitType(3, MasterHex.ARROWS);

        h[14][4].setTerrain('S');
        h[14][4].setLabel(111);
        h[14][4].setLabelSide(5);
        h[14][4].setExitType(4, MasterHex.ARROWS);

        // Derive entrances from exits.
        Iterator it = hexes.iterator();
        while (it.hasNext())
        {
            MasterHex hex = (MasterHex)it.next();
            int i = hex.getXCoord();
            int j = hex.getYCoord();
            for (int k = 0; k < 6; k++)
            {
                int gateType = hex.getExitType(k);
                if (gateType != MasterHex.NONE)
                {
                    switch (k)
                    {
                        case 0:
                            h[i][j - 1].setEntranceType(3, gateType);
                            break;
                        case 1:
                            h[i + 1][j].setEntranceType(4, gateType);
                            break;
                        case 2:
                            h[i + 1][j].setEntranceType(5, gateType);
                            break;
                        case 3:
                            h[i][j + 1].setEntranceType(0, gateType);
                            break;
                        case 4:
                            h[i - 1][j].setEntranceType(1, gateType);
                            break;
                        case 5:
                            h[i - 1][j].setEntranceType(2, gateType);
                        break;
                    }
                }
            }
        }

        // Add references to neighbor hexes.
        it = hexes.iterator();
        while (it.hasNext())
        {
            MasterHex hex = (MasterHex)it.next();
            int i = hex.getXCoord();
            int j = hex.getYCoord();

            if (hex.getExitType(0) != MasterHex.NONE ||
                hex.getEntranceType(0) != MasterHex.NONE)
            {
                hex.setNeighbor(0, h[i][j - 1]);
            }
            if (hex.getExitType(1) != MasterHex.NONE ||
                hex.getEntranceType(1) != MasterHex.NONE)
            {
                hex.setNeighbor(1, h[i + 1][j]);
            }
            if (hex.getExitType(2) != MasterHex.NONE ||
                hex.getEntranceType(2) != MasterHex.NONE)
            {
                hex.setNeighbor(2, h[i + 1][j]);
            }
            if (hex.getExitType(3) != MasterHex.NONE ||
                hex.getEntranceType(3) != MasterHex.NONE)
            {
                hex.setNeighbor(3, h[i][j + 1]);
            }
            if (hex.getExitType(4) != MasterHex.NONE ||
                hex.getEntranceType(4) != MasterHex.NONE)
            {
                hex.setNeighbor(4, h[i - 1][j]);
            }
            if (hex.getExitType(5) != MasterHex.NONE ||
                hex.getEntranceType(5) != MasterHex.NONE)
            {
                hex.setNeighbor(5, h[i - 1][j]);
            }
        }
    }


    private void advancePhase()
    {
        game.advancePhase();
        setupPhase();
    }


    public void setupPhase()
    {
        switch (game.getPhase())
        {
            case Game.SPLIT:
                setupSplit();
                break;
            case Game.MOVE:
                setupMove();
                break;
            case Game.FIGHT:
                setupFight();
                break;
            case Game.MUSTER:
                setupMuster();
                break;
            default:
                System.out.println("Bogus phase");
        }
    }


    private void setupPhaseMenu()
    {
        switch (game.getPhase())
        {
            case Game.SPLIT:
                setupSplitMenu();
                break;
            case Game.MOVE:
                setupMoveMenu();
                break;
            case Game.FIGHT:
                setupFightMenu();
                break;
            case Game.MUSTER:
                setupMusterMenu();
                break;
            default:
                System.out.println("Bogus phase");
        }
    }


    private void setupSplit()
    {
        Player player = game.getActivePlayer();
        player.resetTurnState();

        // If there are no markers available, skip forward to movement.
        if (player.getNumMarkersAvailable() == 0)
        {
            advancePhase();
        }
        else
        {
            masterFrame.setTitle(player.getName() + " Turn " +
                game.getTurnNumber() + " : Split stacks");

            setupSplitMenu();

            // Highlight hexes with legions that are 7 high.
            player.highlightTallLegions();
        }
    }


    private void setupSplitMenu()
    {
        phaseMenu.removeAll();

        mi = phaseMenu.add(undoLastSplitAction);
        mi.setMnemonic(KeyEvent.VK_U);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, 0));

        mi = phaseMenu.add(undoAllSplitsAction);
        mi.setMnemonic(KeyEvent.VK_A);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0));

        mi = phaseMenu.add(doneWithSplitsAction);
        mi.setMnemonic(KeyEvent.VK_D);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0));

        phaseMenu.addSeparator();

        mi = phaseMenu.add(withdrawFromGameAction);
        mi.setMnemonic(KeyEvent.VK_W);
    }


    private void setupMove()
    {
        Player player = game.getActivePlayer();

        player.rollMovement();

        masterFrame.setTitle(player.getName() + " Turn " +
            game.getTurnNumber() + " : Movement Roll: " +
            player.getMovementRoll() + " ");

        setupMoveMenu();

        // Highlight hexes with legions that can move.
        game.highlightUnmovedLegions();
    }


    private void setupMoveMenu()
    {
        phaseMenu.removeAll();

        mi = phaseMenu.add(undoLastMoveAction);
        mi.setMnemonic(KeyEvent.VK_U);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, 0));

        mi = phaseMenu.add(undoAllMovesAction);
        mi.setMnemonic(KeyEvent.VK_A);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0));

        mi = phaseMenu.add(doneWithMovesAction);
        mi.setMnemonic(KeyEvent.VK_D);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0));

        if (game.getActivePlayer().getMulligansLeft() > 0)
        {
            phaseMenu.addSeparator();
            mi = phaseMenu.add(takeMulliganAction);
            mi.setMnemonic(KeyEvent.VK_M);
            mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, 0));
        }

        phaseMenu.addSeparator();

        mi = phaseMenu.add(withdrawFromGameAction);
        mi.setMnemonic(KeyEvent.VK_W);
    }


    private void setupFight()
    {
        // Highlight hexes with engagements.
        // If there are no engagements, move forward to the muster phase.
        if (game.highlightEngagements() == 0)
        {
            advancePhase();
        }
        else
        {
            String playerName = game.getActivePlayer().getName();
            masterFrame.setTitle(playerName + " Turn " +
                game.getTurnNumber() + " : Resolve Engagements ");

            setupFightMenu();
        }
    }


    private void setupFightMenu()
    {
        phaseMenu.removeAll();

        mi = phaseMenu.add(doneWithEngagementsAction);
        mi.setMnemonic(KeyEvent.VK_D);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0));

        phaseMenu.addSeparator();

        mi = phaseMenu.add(withdrawFromGameAction);
        mi.setMnemonic(KeyEvent.VK_W);
    }


    private void setupMuster()
    {
        Player player = game.getActivePlayer();
        if (player.isDead())
        {
            advancePhase();
        }
        else
        {
            masterFrame.setTitle(player.getName() + " Turn " +
                game.getTurnNumber() + " : Muster Recruits ");

            setupMusterMenu();

            // Highlight hexes with legions eligible to muster.
            game.highlightPossibleRecruits();

            if (game.getAutoRecruit())
            {
                game.doAutoRecruit();
            }
        }
    }


    private void setupMusterMenu()
    {
        phaseMenu.removeAll();

        mi = phaseMenu.add(undoLastRecruitAction);
        mi.setMnemonic(KeyEvent.VK_U);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, 0));

        mi = phaseMenu.add(undoAllRecruitsAction);
        mi.setMnemonic(KeyEvent.VK_A);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0));

        mi = phaseMenu.add(doneWithTurnAction);
        mi.setMnemonic(KeyEvent.VK_D);
        mi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0));

        phaseMenu.addSeparator();

        mi = phaseMenu.add(withdrawFromGameAction);
        mi.setMnemonic(KeyEvent.VK_W);
    }


    public JFrame getFrame()
    {
        return masterFrame;
    }


    public void clearLegions()
    {
        markers.clear();

        Iterator it = hexes.iterator();
        while (it.hasNext())
        {
            MasterHex hex = (MasterHex)it.next();
            hex.clearLegions();
            hex.unselect();
        }

        repaint();
    }


    public void loadInitialMarkerImages()
    {
        Collection players = game.getPlayers();
        Iterator it = players.iterator();
        while (it.hasNext())
        {
            Player player = (Player)it.next();
            Collection legions = player.getLegions();
            Iterator it2 = legions.iterator();
            while (it2.hasNext())
            {
                Legion legion = (Legion)it2.next();
                Marker marker = new Marker(3 * scale, legion.getImageName(),
                    this, null);
                legion.setMarker(marker);
                MasterHex hex = legion.getCurrentHex();
                hex.alignLegions();
                moveMarkerToBottom(legion);
            }
        }
    }


    private void setupIcon()
    {
        if (game != null && !game.isApplet())
        {
            try
            {
                masterFrame.setIconImage(Toolkit.getDefaultToolkit().getImage(
                    getClass().getResource(Chit.getImagePath(
                    Creature.colossus.getImageName()))));
            }
            catch (NullPointerException e)
            {
                System.out.println(e.toString() + " Couldn't find " +
                    Creature.colossus.getImageName());
                game.dispose();
            }
        }
    }


    public static int findScale()
    {
        int scale = 16;

        // Make sure that the board fits on the screen.
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        if (d.height < 1000)
        {
            scale = scale * d.height / 1000;
        }

        return scale;
    }


    public Game getGame()
    {
        return game;
    }


    /** Do a brute-force search through the hex array, looking for
     *  a match.  Return the hex, or null if none is found. */
    public static MasterHex getHexFromLabel(String label)
    {
        Iterator it = hexes.iterator();
        while (it.hasNext())
        {
            MasterHex hex = (MasterHex)it.next();
            if (hex.getLabel().equals(label))
            {
                return hex;
            }
        }

        System.out.println("Could not find hex " + label);
        return null;
    }


    /** Return the MasterHex that contains the given point, or
     *  null if none does. */
    private MasterHex getHexContainingPoint(Point point)
    {
        Iterator it = hexes.iterator();
        while (it.hasNext())
        {
            MasterHex hex = (MasterHex)it.next();
            if (hex.contains(point))
            {
                return hex;
            }
        }

        return null;
    }


    /** Return the topmost Legion whose marker contains the given point,
     *  or null if none does. */
    private Legion getLegionAtPoint(Point point)
    {
        Iterator it = markers.iterator();
        while (it.hasNext())
        {
            Marker marker = (Marker)it.next();
            if (marker != null && marker.contains(point))
            {
                return marker.getLegion();
            }
        }

        return null;
    }


    public static void unselectAllHexes()
    {
        Iterator it = hexes.iterator();
        while (it.hasNext())
        {
            MasterHex hex = (MasterHex)it.next();
            if (hex.isSelected())
            {
                hex.unselect();
                hex.repaint();
            }
        }
    }


    public static void unselectHexByLabel(String label)
    {
        Iterator it = hexes.iterator();
        while (it.hasNext())
        {
            MasterHex hex = (MasterHex)it.next();
            if (hex.isSelected() && label.equals(hex.getLabel()))
            {
                hex.unselect();
                hex.repaint();
                return;
            }
        }
    }


    public static void unselectHexesByLabels(Set labels)
    {
        Iterator it = hexes.iterator();
        while (it.hasNext())
        {
            MasterHex hex = (MasterHex)it.next();
            if (hex.isSelected() && labels.contains(hex.getLabel()))
            {
                hex.unselect();
                hex.repaint();
            }
        }
    }


    public static void selectHexByLabel(String label)
    {
        Iterator it = hexes.iterator();
        while (it.hasNext())
        {
            MasterHex hex = (MasterHex)it.next();
            if (!hex.isSelected() && label.equals(hex.getLabel()))
            {
                hex.select();
                hex.repaint();
                return;
            }
        }
    }


    public static void selectHexesByLabels(Set labels)
    {
        Iterator it = hexes.iterator();
        while (it.hasNext())
        {
            MasterHex hex = (MasterHex)it.next();
            if (!hex.isSelected() && labels.contains(hex.getLabel()))
            {
                hex.select();
                hex.repaint();
            }
        }
    }


    /** Clear all entry side and teleport information from all hexes
     *  not occupied by a friendly legion. */
    public void clearAllNonFriendlyOccupiedEntrySides(Player player)
    {
        Iterator it = hexes.iterator();
        while (it.hasNext())
        {
            MasterHex hex = (MasterHex)it.next();
            if (hex.getNumFriendlyLegions(player) == 0)
            {
                hex.clearAllEntrySides();
                hex.setTeleported(false);
            }
        }
    }


    /** Clear all entry side and teleport information from all hexes. */
    public void clearAllEntrySides()
    {
        Iterator it = hexes.iterator();
        while (it.hasNext())
        {
            MasterHex hex = (MasterHex)it.next();
            hex.clearAllEntrySides();
            hex.setTeleported(false);
        }
    }


    public void moveMarkerToTop(Legion legion)
    {
        Marker marker = legion.getMarker();
        markers.remove(marker);
        markers.add(0, marker);
    }


    public void moveMarkerToBottom(Legion legion)
    {
        Marker marker = legion.getMarker();
        markers.remove(marker);
        markers.add(marker);
    }


    public void removeMarker(Legion legion)
    {
        Marker marker = legion.getMarker();
        markers.remove(marker);
    }


    public void mousePressed(MouseEvent e)
    {
        Point point = e.getPoint();

        Legion legion = getLegionAtPoint(point);

        if (legion != null)
        {
            // Move the clicked-on legion to the top of the z-order.
            moveMarkerToTop(legion);

            Player player = legion.getPlayer();

            // What to do depends on which mouse button was used
            // and the current phase of the turn.

            // Right-click means to show the contents of the
            // legion.
            if (((e.getModifiers() & InputEvent.BUTTON2_MASK) ==
                InputEvent.BUTTON2_MASK) || ((e.getModifiers() &
                InputEvent.BUTTON3_MASK) == InputEvent.BUTTON3_MASK))
            {
                new ShowLegion(masterFrame, legion, point,
                    game.getAllStacksVisible() ||
                    player == game.getActivePlayer());
                return;
            }
            else
            {
                // Only the current player can manipulate his legions.
                if (player == game.getActivePlayer())
                {
                    game.actOnLegion(legion);
                    return;
                }
            }
        }

        // No hits on chits, so check map.

        MasterHex hex = getHexContainingPoint(point);
        if (hex != null)
        {
            if (((e.getModifiers() & InputEvent.BUTTON2_MASK) ==
                InputEvent.BUTTON2_MASK) || ((e.getModifiers() &
                InputEvent.BUTTON3_MASK) == InputEvent.BUTTON3_MASK))
            {
                lastPoint = point;
                popupMenu.setLabel(hex.getDescription());
                popupMenu.show(e.getComponent(), point.x, point.y);

                return;
            }

            // Otherwise, the action to take depends on the phase.
            game.actOnHex(hex);
            hex.repaint();
            return;
        }

        // No hits on chits or map, so re-highlight.
        game.actOnMisclick();
    }


    public void mouseReleased(MouseEvent e)
    {
    }


    public void mouseClicked(MouseEvent e)
    {
    }


    public void mouseEntered(MouseEvent e)
    {
    }


    public void mouseExited(MouseEvent e)
    {
    }


    public void itemStateChanged(ItemEvent e)
    {
        JMenuItem source = (JMenuItem)e.getSource();
        String text = source.getText();
        boolean selected = (e.getStateChange() == ItemEvent.SELECTED);

        if (text.equals(Game.sAutosave))
        {
            game.setAutosave(selected);
        }
        else if (text.equals(Game.sAllStacksVisible))
        {
            game.setAllStacksVisible(selected);
        }
        else if (text.equals(Game.sAutoRecruit))
        {
            game.setAutoRecruit(selected);
        }
        else if (text.equals(Game.sAutoPickRecruiter))
        {
            game.setAutoPickRecruiter(selected);
        }
        else if (text.equals(Game.sShowStatusScreen))
        {
            game.setShowStatusScreen(selected);
        }
        else if (text.equals(Game.sShowDice))
        {
            game.setShowDice(selected);
        }
        else if (text.equals(Game.sAntialias))
        {
            game.setAntialias(selected);
        }
        else if (text.equals(Game.sChooseMovement))
        {
            game.setChooseMovement(selected);
        }
    }


    public void windowActivated(WindowEvent e)
    {
    }


    public void windowClosed(WindowEvent e)
    {
    }


    public void windowClosing(WindowEvent e)
    {
        game.dispose();
    }


    public void windowDeactivated(WindowEvent e)
    {
    }


    public void windowDeiconified(WindowEvent e)
    {
    }


    public void windowIconified(WindowEvent e)
    {
    }


    public void windowOpened(WindowEvent e)
    {
    }


    public void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        Rectangle rectClip = g.getClipBounds();

        // Abort if called too early.
        if (rectClip == null)
        {
            return;
        }

        Iterator it = hexes.iterator();
        while (it.hasNext())
        {
            MasterHex hex = (MasterHex)it.next();
            if (rectClip.intersects(hex.getBounds()))
            {
                hex.paint(g);
            }
        }

        // Paint markers in reverse order.
        ListIterator lit = markers.listIterator(markers.size());
        while (lit.hasPrevious())
        {
            Marker marker = (Marker)lit.previous();
            if (marker != null && rectClip.intersects(marker.getBounds()))
            {
                marker.paintComponent(g);
            }
        }
    }


    public Dimension getMinimumSize()
    {
        return new Dimension(64 * scale, 56 * scale);
    }


    public Dimension getPreferredSize()
    {
        return getMinimumSize();
    }


    public static void main(String [] args)
    {
        game = new Game();
        new MasterBoard(game);
    }
}
