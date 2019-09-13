/**
 * Copyright (c) 2016, 2019 Gluon Software
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse
 *    or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gluonhq.devoxx.serverless.sessions;

import javax.json.*;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.gluonhq.devoxx.serverless.util.ConferenceUtil.isNewCfpURL;

public class SessionsRetriever {

    private static final Logger LOGGER = Logger.getLogger(SessionsRetriever.class.getName());

    private static final Client client = ClientBuilder.newClient();
    private static JsonArray talks = null;

    public String retrieve(String cfpEndpoint, String conferenceId) throws IOException {
        WebTarget target = client.target(cfpEndpoint);
        if (isNewCfpURL(cfpEndpoint)) {
            target = target.path("public").path("schedules/");
        } else {
            target = target.path("conferences").path(conferenceId).path("schedules/");
        }
        Response schedules = target.request().get();
        if (schedules.getStatus() == Response.Status.OK.getStatusCode()) {
            try (JsonReader schedulesReader = Json.createReader(new StringReader(schedules.readEntity(String.class)))) {
                List<String> dayLinks = schedulesReader.readObject().getJsonArray("links").getValuesAs(JsonObject.class).stream()
                        .filter(schedule -> schedule.containsKey("href"))
                        .map(schedule -> schedule.getString("href").replaceFirst("http://", "https://"))
                        .collect(Collectors.toList());

                JsonArrayBuilder sessions = Json.createArrayBuilder();
                for (String dayLink : dayLinks) {
                    Response slots = client.target(dayLink).request().get();
                    if (slots.getStatus() == Response.Status.OK.getStatusCode()) {
                        try (JsonReader slotsReader = Json.createReader(new StringReader(slots.readEntity(String.class)))) {
                            if (isNewCfpURL(cfpEndpoint)) {
                                LOGGER.log(Level.INFO, "New API");
                                slotsReader.readArray().getValuesAs(JsonObject.class).stream()
                                        .map(s -> updateSession(s, cfpEndpoint))
                                        .forEach(sessions::add);
                            } else {
                                LOGGER.log(Level.INFO, "Old API");
                                slotsReader.readObject().getJsonArray("slots").getValuesAs(JsonObject.class).stream()
                                        .filter(slot -> (slot.containsKey("talk") && slot.get("talk").getValueType() == JsonValue.ValueType.OBJECT) ||
                                                (slot.containsKey("break") && slot.get("break").getValueType() == JsonValue.ValueType.OBJECT))
                                        .forEach(sessions::add);
                            }
                        }
                    } else {
                        LOGGER.log(Level.WARNING, "Failed processing link " + dayLink + ": " + slots.readEntity(String.class));
                    }
                }
                return sessions.build().toString();
            }
        } else {
            throw new IOException(new WebApplicationException(schedules));
        }
    }

    private JsonObject updateSession(JsonObject source, String cfpEndpoint) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("slotId",         source.get("id"));
        builder.add("roomId",         source.get("roomId"));
        builder.add("roomName",       source.get("roomName"));
        builder.add("day",       "");
        builder.add("fromTime",       source.get("fromDate"));
        builder.add("toTime",         source.get("toDate"));
        builder.add("aBreak",    "");

        final int talkId = source.getInt("talkId", 0);
        if (talkId != 0) {
            JsonObject talk = fetchTalk(cfpEndpoint, talkId);
            builder.add("talk", talk == JsonValue.EMPTY_JSON_OBJECT ? JsonValue.NULL : talk);
        }
        return builder.build();
    }

    private JsonObject fetchTalk(String cfpEndpoint, int talkId) {
        if (talks == null) {
            final Response response = client.target(cfpEndpoint).path("public").path("talks").request().get();
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                try (JsonReader talksReader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
                    talks = talksReader.readArray();
                }
            }
        }
        return talks.getValuesAs(JsonObject.class).stream()
                .filter(t -> t.getInt("id", 0) == talkId)
                .map(t -> updateTalk(t))
                .findFirst().orElse(JsonValue.EMPTY_JSON_OBJECT);
    }

    private JsonObject updateTalk(JsonObject source) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("id",            source.get("id"));
        builder.add("title",         source.get("title"));
        builder.add("talkType",      source.get("sessionTypeName"));
        builder.add("track",         source.get("trackName"));
        builder.add("trackId",       source.get("trackId"));
        builder.add("lang",          "");
        builder.add("audienceLevel", "");
        builder.add("summary",       source.get("description"));
        builder.add("summaryAsHtml", "");
        builder.add("tags",
                source.containsKey("tags") ? source.getJsonArray("tags") : emptyJsonArray());
        builder.add("speakers",
                source.containsKey("speakers") ? source.getJsonArray("speakers") : emptyJsonArray());
        return builder.build();
    }

    private JsonArray emptyJsonArray() {
        return Json.createArrayBuilder().build();
    }
}
