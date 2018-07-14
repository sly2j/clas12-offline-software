/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.rec.ltcc;
import java.util.List;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.geom.prim.Vector3D;

/**
 *
 * Matched track from TBTrack for an LTCC cluster
 * 
 * @author sly2j
 */
public final class LTCCTrackMatcher {

    public static void matchTracks(List<LTCCCluster> clusters,
                                   DataEvent event) {
        if (event.hasBank("TimeBasedTrkg::TBTracks")) {
            DataBank bankDC = event.getBank("TimeBasedTrkg::TBTracks");
            for (LTCCCluster cluster : clusters) {
                doTrackMatching(cluster, bankDC);
            }
        }
    }
    
    /**
     * Get the matching track to this cluster, and update the cluster position
     * @param cluster input cluster
     * @param bankDC  TBTracks bank
     */
    private static void doTrackMatching(LTCCCluster cluster, DataBank bankDC) {
        if (cluster.isGood()) {
            int trackID = findTrack(cluster, bankDC);
            if (trackID > 0) {
                cluster.addTrack(trackID, getPositionFromTracking(cluster, bankDC, trackID));
            }
        }
    }
    
    /**
     * 
     * Find track that matches with the cluster.
     * Currently looks for the highest momentum track in the same sector.
     * 
     * @param cluster
     * @param TBTracks 
     * @return 
     */
    private static int findTrack(LTCCCluster cluster, 
                                 DataBank bankDC) {
        double maxMom = -1.;
        int id = -1;
        for (int i = 0; i < bankDC.rows(); ++i) {
            if (bankDC.getShort("sector", i) == cluster.getSector()) {
                double mom = getTrackMomentum(bankDC, i);
                if (mom > maxMom) {
                    // Is the right sector and the highest momentum track?
                    id = i;
                    maxMom = mom;
                }                            
            }
        }
        return id;
    }
    /**
     * Utility function, return the track momentum a track.
     * 
     * @param bankDC TimeBasedTrkg::TBTracks
     * @param row    track we need the momentum of
     * @return track momentum, or -1 if failure
     */
    private static double getTrackMomentum(DataBank bankDC, int row) {
        double px = bankDC.getFloat("p0_x", row);
        double py = bankDC.getFloat("p0_y", row);
        double pz = bankDC.getFloat("p0_z", row);
        if (px < 0 || py < 0 || pz < 0) {
            return -1.;
        }
        return Math.sqrt(px*px + py*py + pz*pz);
    }
    private static Vector3D getPositionFromTracking(LTCCCluster cluster,
                                             DataBank bankDC, 
                                             int row) {
        // track position at 3rd cross
        double xc = bankDC.getFloat("c3_x", row);
        double yc = bankDC.getFloat("c3_y", row);
        double zc = bankDC.getFloat("c3_z", row);
        double ux = bankDC.getFloat("c3_ux", row);
        double uy = bankDC.getFloat("c3_uy", row);
        double uz = bankDC.getFloat("c3_uz", row);
        // z position of the cluster center
        double z = cluster.getPosition().z();
        // distance between zv and z
        double delta = (z - zc);
        // get extrapolated position at the z-position of the cluster center
        return new Vector3D(
                xc + ux * delta,
                yc + uy * delta,
                zc + uz * delta);
    }      
}
