package org.example;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class SpaceInvadersApp extends Application {

    private InputStream in;
    private OutputStream out;
    private final String HOST = "localhost";
    private final int PORT = 4444;

    private final Pane root = new Pane();

    private double t = 0;

    private int enemiesCount;

    private final Sprite player = new Sprite(300, 750, 40, 40, "player", Color.BLUE);
    private final Sprite coPlayer = new Sprite(300, 750, 40, 40, "player", Color.RED);

    private boolean isPlay = false;
    private String sessionId;
    private Parent createContent() {
        root.setPrefSize(600, 800);

        Image playerImage = new Image("gamer.png");
        player.setFill(new ImagePattern(playerImage));

        Image coPlayerImage = new Image("coPlayer.png");
        coPlayer.setFill(new ImagePattern(coPlayerImage));

        root.getChildren().add(player);
        root.getChildren().add(coPlayer);

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                update();
            }
        };

        timer.start();

        nextLevel();

        return root;
    }

    private void nextLevel() {
        for (int i = 0; i < 5; i++) {
            Sprite s = new Sprite(90 + i*100, 150, 30, 30, "enemy", Color.RED);
            Image image = new Image("enemy.png");
            s.setFill(new ImagePattern(image));

            enemiesCount ++;

            root.getChildren().add(s);
        }
    }

    private List<Sprite> sprites() {
        return root.getChildren().stream().map(n -> (Sprite)n).collect(Collectors.toList());
    }

    private void update() {

        if (isPlay) {

            if (enemiesCount == 0) {
                nextLevel();
            }

            t += 0.016;

            AtomicInteger i = new AtomicInteger(0);
            sprites().forEach(s -> {
                switch (s.type) {

                    case "enemybullet":
                        s.moveDown();

                        if (s.getBoundsInParent().intersects(player.getBoundsInParent())) {
                            player.dead = true;
                            s.dead = true;
                        }

                        if (s.getBoundsInParent().intersects(coPlayer.getBoundsInParent())) {
                            coPlayer.dead = true;
                            s.dead = true;
                        }
                        break;

                    case "playerbullet":
                        s.moveUp();

                        sprites().stream().filter(e -> e.type.equals("enemy")).forEach(enemy -> {
                            if (s.getBoundsInParent().intersects(enemy.getBoundsInParent())) {
                                enemy.dead = true;
                                s.dead = true;

                                enemiesCount--;
                            }
                        });

                        break;

                    case "enemy":
                        if (t > 2) {
                            if (Math.random() < 0.3) {
                                try {
                                    SuperPacket enemyBulletPacket = SuperPacket.create(8);
                                    enemyBulletPacket.setValue(1, sessionId);
                                    enemyBulletPacket.setValue(2, i.get());

                                    out.write(enemyBulletPacket.toByteArray());
                                    out.flush();
                                } catch (IOException e) {
                                    throw new IllegalArgumentException(e);
                                }
                            }
                        }

                        break;
                }
                i.incrementAndGet();
            });

            root.getChildren().removeIf(n -> {
                Sprite s = (Sprite) n;
                return s.dead;
            });

            if (t > 2) {
                t = 0;
            }
        }
    }

    private void shoot(Sprite who) {
        Sprite s = new Sprite((int) who.getTranslateX() + 20, (int) who.getTranslateY(), 12, 20, who.type + "bullet", Color.BLACK);
        s.setFill(new ImagePattern(new Image("fireball.png")));

        root.getChildren().add(s);
    }

    @Override
    public void start(Stage stage) throws Exception {

        Scene scene = new Scene(createContent());
        scene.setFill(new ImagePattern(new Image("space.jpg")));
        sessionId = UUID.randomUUID().toString().substring(0,7);
        System.out.println("[Client#" + sessionId + "]: created");

        Socket socket = new Socket(HOST, PORT);

        in = socket.getInputStream();
        out = socket.getOutputStream();

        new Thread(() -> {
            try {
                while (true) {
                    byte[] serverResponse = readInput(in);

                    if (serverResponse == null) break;

                    Platform.runLater(() -> {
                        SuperPacket packet = SuperPacket.parse(serverResponse);

                        if (packet.getType() == 1) {
                            isPlay = true;
                        }

                        if (packet.getType() == 8) {
                            int i = packet.getValue(2, Integer.class);
                            shoot(sprites().get(i));
                        }

                        if (packet.getType() != 1 && packet.getType() != 8 && !packet.getValue(1, String.class).contains(sessionId)) {

                            if (packet.getType() == 5) {
                                coPlayer.moveLeft();
                            } else if (packet.getType() == 6) {
                                coPlayer.moveRight();
                            } else if (packet.getType() == 7) {
                                shoot(coPlayer);
                            }
                        }
                    });

                }

            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();

        scene.setOnKeyPressed(e -> {
            try {
                KeyCode keyCode = e.getCode();
                if (keyCode.equals(KeyCode.A)) {
                    player.moveLeft();

                    SuperPacket moveLeftPacket = SuperPacket.create(5);
                    moveLeftPacket.setValue(1, sessionId);

                    out.write(moveLeftPacket.toByteArray());
                    out.flush();
                } else if (keyCode.equals(KeyCode.D)) {
                    player.moveRight();

                    SuperPacket moveRightPacket = SuperPacket.create(6);
                    moveRightPacket.setValue(1, sessionId);

                    out.write(moveRightPacket.toByteArray());
                    out.flush();
                } else if (keyCode.equals(KeyCode.SPACE)) {
                    shoot(player);

                    SuperPacket shootPacket = SuperPacket.create(7);
                    shootPacket.setValue(1, sessionId);

                    out.write(shootPacket.toByteArray());
                    out.flush();
                } else if (keyCode.equals(KeyCode.ESCAPE)) {
                    try {
                        socket.close();
                        System.exit(0);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            } catch (IOException exception) {
                throw new IllegalArgumentException(exception);
            }
        });

        stage.setScene(scene);
        stage.show();
    }

    private static class Sprite extends Rectangle {
        boolean dead = false;
        final String type;

        Sprite(int x, int y, int w, int h, String type, Color color) {
            super(w, h, color);

            this.type = type;
            setTranslateX(x);
            setTranslateY(y);
        }

        void moveLeft() {
            setTranslateX(getTranslateX() - 5);
        }

        void moveRight() {
            setTranslateX(getTranslateX() + 5);
        }

        void moveUp() {
            setTranslateY(getTranslateY() - 5);
        }

        void moveDown() {
            setTranslateY(getTranslateY() + 5);
        }
    }

    private byte[] extendArray(byte[] oldArray) {
        int oldSize = oldArray.length;
        byte[] newArray = new byte[oldSize * 2];
        System.arraycopy(oldArray, 0, newArray, 0, oldSize);
        return newArray;
    }

    private byte[] readInput(InputStream stream) throws IOException {
        int b;
        byte[] buffer = new byte[10];
        int counter = 0;
        while ((b = stream.read()) > -1) {
            buffer[counter++] = (byte) b;
            if (counter >= buffer.length) {
                buffer = extendArray(buffer);
            }
            if (counter > 2 && SuperPacket.compareEndOfPacket(buffer, counter - 1)) {
                break;
            }
        }
        byte[] data = new byte[counter];
        System.arraycopy(buffer, 0, data, 0, counter);
        return data;
    }

    public static void main(String[] args) throws IOException {
        launch(args);
    }


}