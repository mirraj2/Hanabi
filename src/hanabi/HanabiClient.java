package hanabi;

import jasonlib.Json;
import jasonlib.swing.component.GButton;
import jasonlib.swing.component.GFrame;
import jasonlib.swing.component.GLabel;
import jasonlib.swing.component.GPanel;
import jasonlib.swing.component.GTextField;
import jasonlib.swing.global.Components;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import jexxus.client.ClientConnection;
import jexxus.common.Connection;
import jexxus.common.ConnectionListener;
import jexxus.server.ServerConnection;
import net.miginfocom.swing.MigLayout;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import com.google.common.base.Throwables;

public class HanabiClient extends GPanel implements ConnectionListener {

  private static final Logger logger = Logger.getLogger(HanabiClient.class);

  private static final boolean ROTATE_SELF_TO_TOP = false;

  private ClientConnection conn = new ClientConnection(this, "home.jasonmirra.com", 19883, false);
  private String username;
  private Json state;
  private JComponent leftSide = new JPanel(new MigLayout("insets 0, gap 0"));
  private JTextArea output = new JTextArea();
  private GLabel discardLabel;
  private DiscardPanel discardPanel = new DiscardPanel();
  private boolean loggedIn = false;
  private GButton newGameButton;

  private HanabiClient() {
    setLayout(new MigLayout("insets 20, gap 0"));
    setOpaque(true);
    setBackground(new Color(240, 240, 240));
    initLoginUI();
  }

  public void play(int index) {
    send(Json.object().with("command", "play").with("index", index));
  }

  public void discard(int index) {
    send(Json.object().with("command", "discard").with("index", index));
  }

  public void colorHint(String target, int index) {
    send(Json.object().with("command", "colorHint").with("target", target).with("index", index));
  }

  public void rankHint(String target, int index) {
    send(Json.object().with("command", "rankHint").with("target", target).with("index", index));
  }

  private JComponent createRightSide() {
    JPanel ret = new GPanel();

    JScrollPane scroll = new JScrollPane(output);
    final JTextField chatbox = new JTextField();

    output.setEditable(false);
    output.setLineWrap(true);
    output.setWrapStyleWord(true);

    ret.add(scroll, "width 100%, height 70%, wrap 10");
    chatbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        send(Json.object().with("command", "chat")
            .with("message", username + ": " + chatbox.getText()));
        chatbox.setText("");
      }
    });
    ret.add(chatbox, "width 100%, height pref!, wrap 10");
    ret.add(discardLabel = new GLabel("Discard Pile").bold(), "wrap 10");
    ret.add(discardPanel, "width 100%, height 30%, wrap 10");
    ret.add(newGameButton = new GButton(newGameAction));

    return ret;
  }

  private Action newGameAction = new AbstractAction("New Game") {
    @Override
    public void actionPerformed(ActionEvent e) {
      int i =
          JOptionPane.showConfirmDialog(HanabiClient.this,
              "This will reset the server kicking all players out" +
                  " of their current game. Are you sure you want to do this?", "Reset Server?", JOptionPane.YES_NO_OPTION);
      if (i == JOptionPane.YES_OPTION) {
        send(Json.object().with("command", "reset"));
      }
    }
  };

  private boolean inGame() {
    return !state.getJson("players").isEmpty();
  }

  private void refreshUI() {
    if (!loggedIn) {
      loggedIn = true;
      removeAll();
      add(leftSide, "width 50%, height 100%");
      add(createRightSide(), "width 50%, height 100%");
      Components.refresh(this);
    }

    leftSide.removeAll();
    newGameButton.setVisible(inGame());
    discardPanel.setVisible(inGame());
    discardLabel.setVisible(inGame());

    if (!inGame()) {
      lobbyUI();
    } else {
      gameUI();
    }
    Components.refresh(leftSide);
  }

  private void gameUI() {
    List<Json> players = state.getJson("players").asJsonArray();

    if (ROTATE_SELF_TO_TOP) {
      // rotate this list so that we are the first player in it
      int myIndex = -1;
      for (Json player : players) {
        myIndex++;
        if (player.get("name").equals(username)) {
          break;
        }
      }
      Collections.rotate(players, -myIndex);
    }



    leftSide.add(new GLabel(state.getJson("deck").size() + " cards in deck").bold(), "split 3");
    leftSide.add(new GLabel(state.getInt("cluesLeft") + " clues").bold(), "gapleft 20");
    leftSide.add(new GLabel(state.getInt("mistakesLeft") + " bombs left").bold(), "wrap 10, gapleft 20");

    int height = 100 / players.size();

    leftSide.add(new GLabel("Board").bold(), "wrap 0");
    leftSide.add(new PlayerPanel(this, "Board", state.getJson("board").asJsonArray(), false, false),
        "width 100%, height min(" + height + "%,100), wrap 15");
    boolean myTurn = username.equals(state.get("turn"));

    for (Json player : players) {
      String name = player.get("name");

      boolean hidden = name.equals(username);

      leftSide.add(new GLabel(state.get("turn").equals(name) ? name + " (My Turn)" : name).bold(), "wrap 0");
      leftSide.add(new PlayerPanel(this, name, player.getJson("hand").asJsonArray(), hidden, myTurn),
          "width 100%, height min(" + height + "%,100), wrap 15");
    }

    discardPanel.update(state.getJson("discard"));
  }

  private void lobbyUI() {
    leftSide.add(new GLabel("Waiting to play:").bold(), "wrap 10");
    List<String> watchers = state.getJson("watchers").asStringArray();
    for (String watcher : watchers) {
      leftSide.add(new JLabel(watcher), "wrap 5");
    }
    // if (watchers.size() >= 2) {
    leftSide.add(new GButton(startGameAction), "newline 10");
    // }
  }

  private Action startGameAction = new AbstractAction("Start Game") {
    @Override
    public void actionPerformed(ActionEvent e) {
      send(Json.object().with("command", "start_game"));
    }
  };

  private void initLoginUI() {
    final GTextField usernameField = new GTextField();
    final JLabel label = new GLabel("Connecting to server...").bold();
    add(label, "");
    add(usernameField, "gapleft 10, width 200!");
    usernameField.setVisible(false);

    Executors.newSingleThreadExecutor().execute(new Runnable() {
      @Override
      public void run() {
        try {
          conn.connect(2000);
          logger.debug("Connected!");
          label.setText("Enter your username:");
          usernameField.setVisible(true);
        } catch (Exception e) {
          logger.error("Failed to connect.");
          label.setText("Failed to connect.");
        }
      }
    });

    usernameField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        username = usernameField.getText();
        send(Json.object().with("command", "login").with("user", username));
      }
    });
  }

  private void send(Json json) {
    conn.send(json.asByteArray());
  }

  @Override
  public void receive(byte[] data, Connection from) {
    final Json json = new Json(data);
    logger.debug(json);

    String command = json.get("command");

    if (command.equals("state")) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          state = json.getJson("state");
          refreshUI();
        }
      });
    } else if (command.equals("announce")) {
      output.append(json.get("text") + '\n');
      output.setCaretPosition(output.getDocument().getLength());
    }
  }

  @Override
  public void clientConnected(ServerConnection conn) {}

  @Override
  public void connectionBroken(Connection broken, boolean forced) {}

  public static void main(String[] args) {
    BasicConfigurator.configure();
    logger.info("Booting up!");

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        try {
          UIManager.setLookAndFeel(
              UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e) {
          throw Throwables.propagate(e);
        }

        new GFrame("Hanabi").content(new HanabiClient()).size(1024, 750).start();
      }
    });
  }

}
