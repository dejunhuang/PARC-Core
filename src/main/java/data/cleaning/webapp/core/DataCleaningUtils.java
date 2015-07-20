package data.cleaning.webapp.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import data.cleaning.core.service.dataset.DatasetService;
import data.cleaning.core.service.dataset.impl.Constraint;
import data.cleaning.core.service.dataset.impl.Dataset;
import data.cleaning.core.service.dataset.impl.DatasetServiceImpl;
import data.cleaning.core.service.dataset.impl.InfoContentTable;
import data.cleaning.core.service.dataset.impl.MasterDataset;
import data.cleaning.core.service.dataset.impl.Record;
import data.cleaning.core.service.dataset.impl.TargetDataset;
import data.cleaning.core.service.matching.MatchingService;
import data.cleaning.core.service.matching.impl.Match;
import data.cleaning.core.service.matching.impl.MatchingServiceImpl;
import data.cleaning.core.service.repair.RepairService;
import data.cleaning.core.service.repair.impl.Candidate;
import data.cleaning.core.service.repair.impl.RepairServiceImpl;
import data.cleaning.core.service.repair.impl.Violations;
import data.cleaning.core.utils.Config;
import data.cleaning.core.utils.Pair;
import data.cleaning.core.utils.objectives.ChangesObjective;
import data.cleaning.core.utils.objectives.CustomCleaningObjective;
import data.cleaning.core.utils.objectives.Objective;
import data.cleaning.core.utils.objectives.PrivacyObjective;
import data.cleaning.core.utils.search.Search;
import data.cleaning.core.utils.search.SimulAnnealEps;

public class DataCleaningUtils {
	private DatasetService datasetService = new DatasetServiceImpl();
	private RepairService repairService = new RepairServiceImpl();
	private MatchingService matchingService = new MatchingServiceImpl();
	
	private float simThreshold;
	private Search search;
	
	private void initConfig () {
		simThreshold = 0.7f;
	}
	
	private Pair<Objective, Set<Objective>> constructEpsObjective(
			Constraint constraint, MasterDataset mDataset) {
		InfoContentTable table = datasetService.calcInfoContentTable(constraint, mDataset);

		Objective pvtFn = new PrivacyObjective(0d, 0d, true, constraint, table);
		Objective cleanFn = new CustomCleaningObjective(
				Config.EPSILON_CLEANING, 0d, true, constraint, table);
		Objective changesFn = new ChangesObjective(Config.EPSILON_SIZE, 0d,
				true, constraint, table);
		Set<Objective> constraintFns = new HashSet<Objective>();
		constraintFns.add(cleanFn);
		constraintFns.add(changesFn);

		Pair<Objective, Set<Objective>> p = new Pair<Objective, Set<Objective>>();
		p.setO1(pvtFn);
		p.setO2(constraintFns);

		return p;
	}
	
	public void runDataCleaning (TargetDataset tgtDataset, MasterDataset mDataset, Constraint constraint) {
		initConfig();
		
		Violations vios = findVios(tgtDataset, constraint);
		System.out.println("Violations: " + vios);
		List<Record> vioRecs = getVioRecords(tgtDataset, vios);
		System.out.println("Violation Records: " + vioRecs);
		List<Match> matches = getMatching(constraint, vioRecs, mDataset.getRecords(), simThreshold, 
				tgtDataset.getName(), mDataset.getName());
		System.out.println("Matching: " + matches);
		
		double startTemp = Config.START_TEMP_EVP * (double) (1d);
		Pair<Objective, Set<Objective>> obj = constructEpsObjective(constraint, mDataset);
		search = new SimulAnnealEps(obj.getO1(), obj.getO2(), startTemp,
				Config.FINAL_TEMP_EVP, Config.ALPHA_TEMP_EVP,
				Config.BEST_ENERGY_EVP, Config.INIT_STRATEGY,
				Config.IND_NORM_STRAT);
		
		Set<Candidate> candidates = getRecommendations (constraint, matches, search, tgtDataset, mDataset, true);
		System.out.println("Recommendations: " + candidates);
	}
	
	/*
	 * find violations w.r.t the constraint in the dataset
	 */
	public Violations findVios (Dataset tgtDataset, Constraint constraint) {
		List<Record> records = tgtDataset.getRecords();
		Violations allViols = repairService.calcViolations(records, constraint);
		return allViols;
	}
	
	//get list of records that have violations in the original dataset
	private List<Record> getVioRecords(Dataset originalDataset, Violations vios) {
		List<Set<Long>> viosList = repairService.subsetViolsBySize(vios);
		List<Record> records = new ArrayList<Record>();

		for (Set<Long> viols : viosList) {
			for (long v : viols) {
				records.add(originalDataset.getRecord(v));
			}
		}

		return records;
	}
	
	/*
	 * find matching between two datasets
	 */
	public List<Match> getMatching (Constraint constraint,
			List<Record> tgtRecords, List<Record> mRecords, float simThreshold,
			String tgtFileName, String mFileName) {
		List<Match> tgtMatches = matchingService.applyApproxDataMatching(
				constraint, tgtRecords, mRecords, simThreshold,
				tgtFileName, mFileName);

		return tgtMatches;
	}
	
	/*
	 * Load target dataset
	 */
	public TargetDataset loadTargetDataset (String tgtUrl, String tgtFileName,
			String fdUrl, char separator, char quoteChar) {
		return datasetService.loadTargetDataset(tgtUrl, tgtFileName, fdUrl, separator, quoteChar);
	}
	
	/*
	 * Load master dataset
	 */
	public MasterDataset loadMasterDataset (String mUrl, String mFileName,
			String fdUrl, long targetId, char separator, char quoteChar) {
		return datasetService.loadMasterDataset(mUrl, mFileName, fdUrl, targetId, separator, quoteChar);
	};
	
	/*
	 * get list of recommendations w.r.t the list of matching results and searching objective & type
	 */
	public Set<Candidate> getRecommendations (Constraint constraint,
			List<Match> tgtMatches, Search search, TargetDataset tgtDataset,
			MasterDataset mDataset, boolean shdReturnInit) {
		InfoContentTable table = datasetService.calcInfoContentTable(constraint, mDataset);
		return repairService.calcOptimalSolns(constraint, tgtMatches, search, tgtDataset, mDataset, table, shdReturnInit);
	}
	
	public static void main (String[] args) {
		DataCleaningUtils dcu = new DataCleaningUtils();
		
		char separator = ',';
		char quoteChar = '"';
		
		String tgtUrl = "/Users/thomas/Documents/Programming/DataPrivacy/resource/data/testdata1/addressTarget.csv";
		String tgtFileName = "addressTarget";

		String mUrl = "/Users/thomas/Documents/Programming/DataPrivacy/resource/data/testdata1/addressMaster.csv";
		String mFileName = "addressMaster";
		
		String fdUrl = "/Users/thomas/Documents/Programming/DataPrivacy/resource/data/testdata1/address_fd.csv";
		
		TargetDataset target = dcu.loadTargetDataset(tgtUrl, tgtFileName, fdUrl, separator, quoteChar);
		MasterDataset master = dcu.loadMasterDataset(mUrl, mFileName, fdUrl, 1, separator, quoteChar);
		
		dcu.runDataCleaning(target, master, target.getConstraints().get(0));
	}
	
}
