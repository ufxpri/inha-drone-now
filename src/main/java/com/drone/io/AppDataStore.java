package com.drone.io;

import com.drone.model.PilotLicense;
import com.drone.model.Spot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AppDataStore {
    private final Path licenseFile = Path.of("./data/license.txt");
    private final Path spotsFile = Path.of("./data/spots.json");
    private final ObjectMapper mapper;

    public AppDataStore() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            Files.createDirectories(Path.of("./data"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create data directory", e);
        }
    }

    public PilotLicense loadLicense() {
        if (!Files.exists(licenseFile)) return PilotLicense.NONE;
        try {
            String line = Files.readString(licenseFile, StandardCharsets.UTF_8).trim();
            if (line.isEmpty()) return PilotLicense.NONE;
            return PilotLicense.valueOf(line);
        } catch (IOException | IllegalArgumentException e) {
            return PilotLicense.NONE;
        }
    }

    public void saveLicense(PilotLicense license) {
        try {
            Files.createDirectories(licenseFile.getParent());
            Files.writeString(licenseFile, license.name(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save license: " + e.getMessage(), e);
        }
    }

    public List<Spot> loadSpots() {
        if (!Files.exists(spotsFile)) return new ArrayList<>();
        try {
            return mapper.readValue(spotsFile.toFile(), new TypeReference<List<Spot>>() {});
        } catch (IOException e) {
            System.err.println("Failed to load spots: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public void addSpot(Spot spot) {
        List<Spot> spots = loadSpots();
        spots.add(spot);
        writeAllSpots(spots);
    }

    public void removeSpot(Spot spot) {
        List<Spot> spots = loadSpots();
        spots.removeIf(s -> s.equals(spot));
        writeAllSpots(spots);
    }

    private void writeAllSpots(List<Spot> spots) {
        try {
            Files.createDirectories(spotsFile.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(spotsFile.toFile(), spots);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write spots: " + e.getMessage(), e);
        }
    }
}
