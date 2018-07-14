package org.jlab.service.ltcc;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataEvent;
import java.util.List;
import java.util.Arrays;
import org.jlab.io.base.DataBank;

import org.jlab.rec.ltcc.LTCCRawHit;
import org.jlab.rec.ltcc.LTCCHit;
import org.jlab.rec.ltcc.LTCCClusterFinder;
import org.jlab.rec.ltcc.LTCCCluster;
import org.jlab.rec.ltcc.LTCCTrackMatcher;

/**
 * LTCC Reconstruction Engine.
 *
 * @author S. Joosten
 */
public class LTCCEngine extends ReconstructionEngine {

    private static final boolean DEBUG = false;
    private static final List<String> CC_TABLES
            = Arrays.asList("/calibration/ltcc/spe");

    public LTCCEngine() {
        super("LTCC", "joosten", "2.0");
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        if (DEBUG) {
            event.show();
            if (event.hasBank("LTCC::adc")) {
                event.getBank("LTCC::adc").show();
            }
            if (event.hasBank("LTCC::tdc")) {
                event.getBank("LTCC::tdc").show();
            }
        }
        // get run info
        int run = -1;
        if (event.hasBank("RUN::config")) {
            DataBank header = event.getBank("RUN::config");
            run = header.getInt("run", 0);
        }
        List<LTCCRawHit> raw = LTCCRawHit.load(event);
        List<LTCCHit> hits = LTCCHit.load(raw, this.getConstantsManager(), run);
        List<LTCCCluster> clusters = LTCCClusterFinder.findClusters(hits);
        LTCCTrackMatcher.matchTracks(clusters, event);
        LTCCHit.save(hits, event);
        LTCCCluster.save(clusters, event);
        if (DEBUG) {
            event.getBank("LTCC::hits").show();
            event.getBank("LTCC::clusters").show();
        }       
        return true;
    }

        
    @Override
        public boolean init() {
            this.requireConstants(CC_TABLES);
            System.out.println("[LTCC] --> initialization successful...");
            return true;
        }
       
}
