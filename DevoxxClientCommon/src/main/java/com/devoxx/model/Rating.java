package com.devoxx.model;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public class Rating {

    private String conferenceId;
    private ZonedDateTime ratingTime;
    private State state;

    public Rating(String conferenceId, ZonedDateTime ratingTime, State state) {
        this.conferenceId = conferenceId;
        this.ratingTime = ratingTime;
        this.state = state;
    }

    public String getConferenceId() {
        return conferenceId;
    }

    public void setConferenceId(String conferenceId) {
        this.conferenceId = conferenceId;
    }

    public ZonedDateTime getRatingTime() {
        return ratingTime;
    }

    public void setRatingTime(ZonedDateTime ratingTime) {
        this.ratingTime = ratingTime;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public enum State {
        SHOW,
        SHOWN
    }
}
