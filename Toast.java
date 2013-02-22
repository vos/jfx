import com.sun.javafx.Utils;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PopupControl;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * User: Alexander Vos
 * Date: 19.02.13
 * Time: 23:35
 */
public class Toast extends PopupControl {
    private static final Logger log = LoggerFactory.getLogger(Toast.class);

    private static double defaultContentOpacity = 0.9;
    private static int defaultFadeInTime = 600;
    private static int defaultFadeOutTime = 300;

    public static double getDefaultContentOpacity() {
        return defaultContentOpacity;
    }

    public static void setDefaultContentOpacity(double defaultContentOpacity) {
        Toast.defaultContentOpacity = defaultContentOpacity;
    }

    public static int getDefaultFadeInTime() {
        return defaultFadeInTime;
    }

    public static void setDefaultFadeInTime(int defaultFadeInTime) {
        Toast.defaultFadeInTime = defaultFadeInTime;
    }

    public static int getDefaultFadeOutTime() {
        return defaultFadeOutTime;
    }

    public static void setDefaultFadeOutTime(int defaultFadeOutTime) {
        Toast.defaultFadeOutTime = defaultFadeOutTime;
    }

    private static final Timer timer = new Timer("Toast-Timer", true);
    private static volatile boolean alreadyShowing = false;
    private static final Queue<Toast> toastQueue = new LinkedBlockingQueue<>(50);

    public static int clearWaitingToasts() {
        int count = toastQueue.size();
        toastQueue.clear();
        return count;
    }

    private Node content;
    private int duration;
    private boolean autoCenter = true;

    private Window window;
    private Node anchor;
    private double screenX;
    private double screenY;

    private double contentOpacity = defaultContentOpacity;
    private int fadeInTime = defaultFadeInTime;
    private int fadeOutTime = defaultFadeOutTime;

    private TimerTask timerTask;

    public Toast() {
        getStyleClass().add("toast");
        setAutoHide(true);
    }

    public void setContent(Node content) {
        if (content == null) {
            throw new NullPointerException();
        }
        this.content = content;
        content.getStyleClass().add("toast");
        content.getStyleClass().add("content");
        ObservableList<Node> nodes = super.getContent(); // TODO how to set the content?
        nodes.clear();
        nodes.add(content);
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        if (duration <= 0) {
            throw new IllegalArgumentException("duration must be positive");
        }
        this.duration = duration;
    }

    public boolean isAutoCenter() {
        return autoCenter;
    }

    public void setAutoCenter(boolean autoCenter) {
        this.autoCenter = autoCenter;
    }

    public double getContentOpacity() {
        return contentOpacity;
    }

    public void setContentOpacity(double contentOpacity) {
        this.contentOpacity = contentOpacity;
    }

    public int getFadeInTime() {
        return fadeInTime;
    }

    public void setFadeInTime(int fadeInTime) {
        this.fadeInTime = fadeInTime;
    }

    public int getFadeOutTime() {
        return fadeOutTime;
    }

    public void setFadeOutTime(int fadeOutTime) {
        this.fadeOutTime = fadeOutTime;
    }

    @Override
    public void show(Window window) {
        this.show(window, Double.NaN, Double.NaN);
    }

    @Override
    public void show(Window window, double screenX, double screenY) {
        if (window == null) {
            return;
        }
        this.window = window;
        this.anchor = null;
        this.screenX = screenX;
        this.screenY = screenY;
        tryShow();
    }

    public void show(Node anchor) {
        autoCenter = true;
        this.show(anchor, Double.NaN, Double.NaN);
    }

    @Override
    public void show(Node anchor, double screenX, double screenY) {
        if (anchor == null) {
            return;
        }
        this.window = null;
        this.anchor = anchor;
        this.screenX = screenX;
        this.screenY = screenY;
        tryShow();
    }

    public void show(Node anchor, Side side, double offsetX, double offsetY) {
        if (anchor == null) {
            return;
        }
        autoCenter = false;

        HPos hpos = side == Side.LEFT ? HPos.LEFT : side == Side.RIGHT ? HPos.RIGHT : HPos.CENTER;
        VPos vpos = side == Side.TOP ? VPos.TOP : side == Side.BOTTOM ? VPos.BOTTOM : VPos.CENTER;
        // translate from anchor/hpos/vpos/offsetX/offsetY into screenX/screenY
        Point2D point = Utils.pointRelativeTo(anchor, prefWidth(-1), prefHeight(-1), hpos, vpos, offsetX, offsetY, true);
        this.show(anchor, point.getX(), point.getY());
    }

    public void show(Node anchor, Side side) {
        this.show(anchor, side, 0, 0);
    }

    private void tryShow() {
        if (window == null && anchor == null) {
            throw new IllegalStateException("window and anchor node are both null");
        }
        if (alreadyShowing) {
            // if another toast is already showing add this one to the waiting queue
            if (toastQueue.offer(this)) {
                log.debug("toast enqueued: {}", this);
            } else {
                log.error("toast queue exceeded it's capacity");
            }
        } else {
            alreadyShowing = true;
            doShow();
        }
    }

    private void doShow() {
        log.trace("show toast: {}", this);
        if (window != null) {
            if (autoCenter) {
                connectAutoCenterHandler();
            }
            if (Double.isNaN(screenX) || Double.isNaN(screenY)) {
                super.show(window);
            } else {
                super.show(window, screenX, screenY);
            }
        } else { // anchor
            if (autoCenter) {
                Scene scene = anchor.getScene();
                if (scene != null) {
                    window = scene.getWindow();
                }
                if (window == null) {
                    throw new IllegalStateException("anchor node is not attached to a window");
                }
                connectAutoCenterHandler();
            }
            super.show(anchor, Double.isNaN(screenX) ? 0.0 : screenX, Double.isNaN(screenY) ? 0.0 : screenY);
        }
        if (isAutoHide() && duration > 0 && duration != LENGTH_INFINITE) {
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    timerTask = null;
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            Toast.this.hide();
                        }
                    });
                }
            };
            timer.schedule(timerTask, duration);
        }
        FadeTransition transition = new FadeTransition(Duration.millis(fadeInTime), content);
        transition.setFromValue(0.0);
        transition.setToValue(contentOpacity);
        transition.play();
    }

    private void connectAutoCenterHandler() {
        XListener xListener = new XListener();
        super.widthProperty().addListener(xListener);
        YListener yListener = new YListener();
        super.heightProperty().addListener(yListener);
    }

    @Override
    public void hide() {
        log.trace("hide toast: {}", this);
        if (timerTask != null) {
            // cancel the timer if the toast is hidden before it can fire
            timerTask.cancel();
            timerTask = null;
        }
        if (!isShowing()) {
            return;
        }
        FadeTransition transition = new FadeTransition(Duration.millis(fadeOutTime), content);
        transition.setToValue(0.0);
        transition.setOnFinished(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                doHide();
            }
        });
        transition.play();
    }

    private void doHide() {
        super.hide();
        if (toastQueue.isEmpty()) {
            alreadyShowing = false;
        } else {
            // get the next toast and show it (may flicker if not enqueued in the FX thread!?)
            final Toast toast = toastQueue.poll();
            log.debug("toast unqueued: {}", toast);
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    toast.doShow();
                }
            });
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Toast");
        sb.append("{content=").append(content);
        sb.append('}');
        return sb.toString();
    }

    // helper methods

    public static final int LENGTH_SHORT = 2000;
    public static final int LENGTH_LONG = 4000;
    public static final int LENGTH_INFINITE = Integer.MAX_VALUE;

    private static final Color DEFAULT_BACKGROUND_COLOR = new Color(0.2, 0.2, 0.2, 1);
    private static final double DEFAULT_BACKGROUND_ARC = 8.0;
    private static final Insets DEFAULT_CONTENT_MARGIN = new Insets(8, 16, 8, 16);

    public static Toast makeToast(Node content, int duration) {
        Rectangle rect = new Rectangle();
        rect.getStyleClass().add("background");
        rect.setFill(DEFAULT_BACKGROUND_COLOR);
        rect.setArcWidth(DEFAULT_BACKGROUND_ARC);
        rect.setArcHeight(DEFAULT_BACKGROUND_ARC);

        StackPane pane = new StackPane();
        rect.widthProperty().bind(pane.widthProperty());
        rect.heightProperty().bind(pane.heightProperty());
        StackPane.setMargin(content, DEFAULT_CONTENT_MARGIN);
        pane.getChildren().addAll(rect, content);

        Toast toast = new Toast();
        toast.setContent(pane);
        toast.setDuration(duration);
        return toast;
    }

    private static final Color DEFAULT_FILL_COLOR = Color.WHITE;

    public static Toast makeText(String text, boolean wrapText, double maxWidth, double maxHeight, int duration) {
        Label label = new Label(text);
        label.getStyleClass().add("text");
        label.setWrapText(wrapText);
        label.setMaxWidth(maxWidth);
        label.setMaxHeight(maxHeight);
        label.setTextFill(DEFAULT_FILL_COLOR);
        return makeToast(label, duration);
    }

    public static Toast makeText(String text, int duration) {
        return makeText(text, true, 500, 250, duration);
    }

    private class XListener implements ChangeListener<Number> {
        @Override
        public void changed(ObservableValue<? extends Number> observableValue, Number number, Number number2) {
            double x = window.getX() + window.getWidth() / 2 - Toast.super.getWidth() / 2;
            if (!Double.isNaN(screenX)) {
                x += screenX; // use as offset
            }
            Toast.super.setX(x);
        }
    }

    private class YListener implements ChangeListener<Number> {
        @Override
        public void changed(ObservableValue<? extends Number> observableValue, Number number, Number number2) {
            double y = window.getY() + window.getHeight() / 2 - Toast.super.getHeight() / 2;
            if (!Double.isNaN(screenY)) {
                y += screenY; // use as offset
            }
            Toast.super.setY(y);
        }
    }
}
