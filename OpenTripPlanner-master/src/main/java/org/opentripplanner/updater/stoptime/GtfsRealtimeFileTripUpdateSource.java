/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.updater.stoptime;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.updater.JsonConfigurable;
import org.opentripplanner.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.TextFormat;
import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;

/** Reads the GTFS-RT from a local file. */
public class GtfsRealtimeFileTripUpdateSource implements TripUpdateSource, JsonConfigurable {
    private static final Logger LOG =
            LoggerFactory.getLogger(GtfsRealtimeFileTripUpdateSource.class);

    private File file;

    /**
     * True iff the last list with updates represent all updates that are active right now, i.e. all
     * previous updates should be disregarded
     */
    private boolean fullDataset = true;
    
    /**
     * Default agency id that is used for the trip ids in the TripUpdates
     */
    private String feedId;
    
    /**
     * An optional filed used for ASCII input
     */
    private String feedType;

    @Override
    public void configure(Graph graph, JsonNode config) throws Exception {
        this.feedId = config.path("feedId").asText();
        this.file = new File(config.path("file").asText(""));
        this.feedType = config.path("feedType").asText("");
    }

    @Override
    public List<TripUpdate> getUpdates() {
        FeedMessage feedMessage = null;
        List<FeedEntity> feedEntityList = null;
        List<TripUpdate> updates = null;
        fullDataset = true;
        try {
            InputStream is = new FileInputStream(file);
            if (is != null) {
                // Decode message
            	if( this.feedType.equalsIgnoreCase(Constants.FEED_TYPE_ASCII)){
            		InputStreamReader reader = new InputStreamReader(is, Constants.FEED_TYPE_ASCII);
            		FeedMessage.Builder builder = FeedMessage.newBuilder();
            		TextFormat.merge(reader, builder);
            		feedMessage = builder.build();
            	}
            	else{
            		feedMessage = FeedMessage.PARSER.parseFrom(is);
                }
                feedEntityList = feedMessage.getEntityList();
                
                // Change fullDataset value if this is an incremental update
                if (feedMessage.hasHeader()
                        && feedMessage.getHeader().hasIncrementality()
                        && feedMessage.getHeader().getIncrementality()
                                .equals(GtfsRealtime.FeedHeader.Incrementality.DIFFERENTIAL)) {
                    fullDataset = false;
                }
                
                // Create List of TripUpdates
                updates = new ArrayList<TripUpdate>(feedEntityList.size());
                for (FeedEntity feedEntity : feedEntityList) {
                    if (feedEntity.hasTripUpdate()) updates.add(feedEntity.getTripUpdate());
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse gtfs-rt feed at " + file + ":", e);
        }
        return updates;
    }

    @Override
    public boolean getFullDatasetValueOfLastUpdates() {
        return fullDataset;
    }
    
    public String toString() {
        return "GtfsRealtimeFileTripUpdateSource(" + file + ")";
    }

    @Override
    public String getFeedId() {
        return this.feedId;
    }
}
