/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erikw.mediaplayer;

import java.io.File;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 *
 * @author Erik.Walker
 */
public class MediaPlayer extends Application {
      // Let's set up member variables for the model, the view and the controller
    private Media media;    // model: encapsulates the media (audio or video) file
    private javafx.scene.media.MediaPlayer mediaPlayer; // controller: allows operations like play, pause, stop, seek
    private MediaView mediaView;    // view: the actual UI element that will play the view

    private InvalidationListener positionSliderListener; // this will be needed for the position slider
    
    /* jerky slider motion and moving the slider  */
    // we see how the slider move is causing the video to play jerkily - this is because the operation
    // of spawing a new thread call (Platform.runLater) on each update of the slider was excessive.

    // Let's buffer these calls so that we only inch forward the slider when the track has moved ahead
    // by 1% at a time.
    // the movement of the slider will become sligtly jerky
    
    // to solve this problem create a varible that stores completeness of the file
    private double percentComplete = 0.0;

    @Override
    public void start(Stage primaryStage) throws Exception{
        // set up a file chooser to select the file
        TextField fileName = new TextField("No file selected yet");
        fileName.setPrefColumnCount(30);

        Button fileButton = new Button("Choose File");

        HBox fileChooseBox = new HBox(fileName,fileButton);
        HBox.setMargin(fileName,new Insets(0,6,0,6));
        HBox.setMargin(fileButton,new Insets(0,6,0,6));

        // Volume slider to control the volume. A slider control has a way to
        // specify a min, max and initial value for the slider
        Slider volumeSlider = new Slider(0,1,0.3);

        // Play button
        Button playButton = new Button("Play");
        playButton.setPrefWidth(70);

        // Other buttons
        Button stopButton = new Button("Stop");
        Button startButton = new Button("<<");
        Button endButton = new Button(">>");

        // why did the play button need a preferred width while the other buttons did not?
        // its because the play button also doubles as the pause button - if wedid not set
        // a preferreed width, the size of the text box would toggle with the text.

        // the position slider - we will see this is quite difficult to wire up right
        Slider positionSlider = new Slider(0,1,0);
        positionSlider.setPrefWidth(1200);

        // finish the UI set up
        HBox buttonBox = new HBox(volumeSlider, startButton,playButton,stopButton,endButton);
        VBox controlBox = new VBox(positionSlider,buttonBox);
        VBox.setMargin(positionSlider,new Insets(0,6,0,6));
        VBox.setMargin(buttonBox, new Insets(0, 6, 0, 6));

        GridPane gridPane = new GridPane();
        gridPane.add(controlBox, 0, 0);
        gridPane.setPrefWidth(1200);
        gridPane.setPrefHeight(80);

        BorderPane borderPane = new BorderPane();
        primaryStage.setTitle("Media Player");
        primaryStage.setScene(new Scene(borderPane, 1200, 800));
        
        
        
        
        borderPane.setTop(fileChooseBox);
        BorderPane.setMargin(fileChooseBox, new Insets(12));

        borderPane.setBottom(gridPane);
        BorderPane.setMargin(gridPane,new Insets(12));
        


        StackPane stackPane = new StackPane();
        stackPane.setPrefSize(800, 900);
        stackPane.setMaxHeight(900);

        borderPane.setCenter(stackPane);

        primaryStage.show();
        // now wire up the listeners (i.e. the controller)

        // the file chooser listener
        fileButton.setOnAction(e -> {
            //1. get the selected file from the file chooser
            // accept from the user what file they want to play
            FileChooser fileChooser = new FileChooser();
            File mediaFile = fileChooser.showOpenDialog(primaryStage);

            // instantiate the media player and the media view
            // a reminder that if the media player holds an existing file, it needs to be
            // cleaned up
            
            if (mediaFile != null) {
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    // destroys the object
                    mediaPlayer.dispose();
                }
            }

            //2. open a media object around that file and save it in our member variable
            fileName.setText(mediaFile.getAbsolutePath());
            media = new Media(mediaFile.toURI().toString());
            mediaPlayer = new javafx.scene.media.MediaPlayer(media);

            mediaView = new MediaView(mediaPlayer);
            // the media view hosts the video on the screen
            mediaView.setPreserveRatio(true);
            mediaView.setFitHeight(600);

            // occupies the center of the borderPane         
            stackPane.getChildren().addAll(mediaView);
            StackPane.setAlignment(mediaView, Pos.TOP_CENTER);

            //4. wire up the audio (volume) slider. Really simple.
            volumeSlider.valueProperty().bindBidirectional(mediaPlayer.volumeProperty());
            // Before we get to the position slider, which we said is rather complicated,
            // let's understand why the volume slider is NOT complicated. This was because
            // the volumeProperty of the mediaPlayer is both available (it exists) and is
            // read-write. This means that we can simply do a bi-directional bind. Now if either
            // changes, the other will stay the same.


            //5. wire up the position slider. THIs is complicated.
            // as the file plays the user needs to be able to skip forward as needed 
            // In contrast to the volume property, the current time property is not (read-write).
            // This means that we can set up our slider to read the current play position, but
            // we can not directly use it to set the current play position. That's not intuitive
            // because any reasonable user would expect the position slider to work both ways -
            // to move along as the movie plays, and also to be usable to set the position of the movie.

            // TO make this hapen, we will need to listen to updates on the current play position,
            // and as we hear those updates, fire updates to move the position of the slider

            positionSliderListener = observable -> {
                // this is where we have to inch forward the position of the slider.
                // One more complication though: updates from the MediaPlayer currentTimeProperty
                // are NOT fired onthe javafx application thread. Now the only thread on which we
                // can update the UI is the javafx application thread - so we will have to use
                // Platform.runLater to put our operation on the main javafx thread.
                
                // as we get updated from mediaplayer 
                // calculate the postion of the slider
                double percentCompleteNow = mediaPlayer.getCurrentTime().toMillis() /
                        mediaPlayer.getTotalDuration().toMillis();
                // if the value is more than 100% we update slider by inching forward.
                if (Math.abs(percentCompleteNow - percentComplete) > 0.005) {
                    percentComplete = percentCompleteNow;
                    // create a runable object 
                    Runnable r = () -> {
                        // take the current positon of the slider
                        if (!positionSlider.isValueChanging()) {
                            positionSlider.setValue(percentComplete);
                        }
                    };
                    Platform.runLater(r);
                }
            };
            // is an invalidation listener 
            mediaPlayer.currentTimeProperty().addListener(positionSliderListener);
            // Now, as the media file plays, the currentTImeProperty will be updated, and
            // we will be informed of these changes and our positionSliderListener code
            // will be called. BTW - this is a very resource-intensive call - in a professional
            // app we would make sure to inch the slider forward at intervals, not in a continuous
            // loop

        });

        // play button listener
        // serves as a way to toggle between play and pause
        playButton.setOnAction(e -> {
            if (mediaPlayer == null) {
                return;
            }
            // when we pause we change the text of button to play
            if (mediaPlayer.getStatus() == javafx.scene.media.MediaPlayer.Status.PLAYING) {
                mediaPlayer.pause();
                playButton.setText("Play");
            } else {
                mediaPlayer.play();
                playButton.setText("Pause");
            }
        });

        // stop button listener
        stopButton.setOnAction(e -> {
            if (mediaPlayer != null) {
                playButton.setText("Play");
                mediaPlayer.stop();
            }
        });

        // start button listener
        startButton.setOnAction(e->{
            if(mediaPlayer!=null) {
                javafx.scene.media.MediaPlayer.Status status = mediaPlayer.getStatus();
                mediaPlayer.pause();
                mediaPlayer.seek(Duration.ZERO);
                
                if(status == javafx.scene.media.MediaPlayer.Status.PLAYING) {
                    mediaPlayer.play();
                }
            }
        });

        // end button listener
        endButton.setOnAction( e-> {
            if(mediaPlayer!=null) {
                javafx.scene.media.MediaPlayer.Status status = mediaPlayer.getStatus();
                mediaPlayer.pause();
                // we seek to the last second before the end of the file 
                mediaPlayer.seek(mediaPlayer.getTotalDuration().subtract(Duration.seconds(1)));
                if(status == javafx.scene.media.MediaPlayer.Status.PLAYING) {
                    mediaPlayer.play();
                }
            }
        });

        // position slider
        // what we did above was to move the position slider as the file plays. Now,
        // we need to wire up the other direction: we need to detect when the user moves
        // the slider, and accordingly update the position of the movie.
        
        // listen in on the value for the slider
        // update the player postion of the slider 
        // when we are listening we are using a change listener rather than 
        // an invalidation listener
        positionSlider.valueProperty().addListener( (obsVal,oldValue,newValue) -> {
                    if (mediaPlayer == null) {
                        return;
                    }

                    // first store the current status of the media player
                    // then find the new position in the media file to be played
                    // we find this by scaling the new position of the slider
                    // seek to that position
                    // now we did not want to seek on a moving file, so if the initial status
                    // was 'playing', pause the file before the seek, and restart playing after the
                    // seek.
                    Duration seekPosition = Duration.millis(positionSlider.getValue() *
                            mediaPlayer.getTotalDuration().toMillis());
                    javafx.scene.media.MediaPlayer.Status status = mediaPlayer.getStatus();
                    // pause to make sure we are not seeking on the new file.
                    mediaPlayer.pause();
                    mediaPlayer.seek(seekPosition);
                    if (status == javafx.scene.media.MediaPlayer.Status.PLAYING) {
                        mediaPlayer.play();
                    }
                });

    }


    public static void main(String[] args) {
        launch(args);
    }
}
