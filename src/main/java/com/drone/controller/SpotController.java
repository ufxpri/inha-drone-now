package com.drone.controller;

import com.drone.io.AppDataStore;
import com.drone.model.Spot;

import java.util.List;

public class SpotController {
    private final AppDataStore dataStore;

    public SpotController(AppDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public void addSpot(Spot s) {
        dataStore.addSpot(s);
    }

    public void removeSpot(Spot s) {
        dataStore.removeSpot(s);
    }

    public List<Spot> listSpots() {
        return dataStore.loadSpots();
    }
}
