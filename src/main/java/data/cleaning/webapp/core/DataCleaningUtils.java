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
import data.cleaning.core.utils.ProdLevel;
import data.cleaning.core.utils.objectives.ChangesObjective;
import data.cleaning.core.utils.objectives.CustomCleaningObjective;
import data.cleaning.core.utils.objectives.Objective;
import data.cleaning.core.utils.objectives.PrivacyObjective;
import data.cleaning.core.utils.search.Search;
import data.cleaning.core.utils.search.SearchType;
import data.cleaning.core.utils.search.SimulAnnealEps;
import data.cleaning.core.utils.search.SimulAnnealEpsDynamic;
import data.cleaning.core.utils.search.SimulAnnealEpsFlexible;
import data.cleaning.core.utils.search.SimulAnnealEpsLex;
import data.cleaning.core.utils.search.SimulAnnealWeighted;

public class DataCleaningUtils {
	private DatasetService datasetService = new DatasetServiceImpl();
	private RepairService repairService = new RepairServiceImpl();
	private MatchingService matchingService = new MatchingServiceImpl();
	
	private float simThreshold;
	private Search search;
	
	private void initConfig () {
		simThreshold = 0.7f;
	}
	
	private Search initSimulAnneal(MasterDataset mDataset, Constraint constraint) {
		double startTemp = Config.START_TEMP_EVP * (double) (1d);
		Pair<Objective, Set<Objective>> obj = constructEpsObjective(constraint, mDataset);
		Search search = new SimulAnnealEps(obj.getO1(), obj.getO2(), startTemp,
				Config.FINAL_TEMP_EVP, Config.ALPHA_TEMP_EVP,
				Config.BEST_ENERGY_EVP, Config.INIT_STRATEGY,
				Config.IND_NORM_STRAT);
		
		return search;
	}
	
	private Search initWeightedSimulAnneal(MasterDataset mDataset, Constraint constraint) {
		double startTemp = Config.START_TEMP_EVP * (double) (1d);
		Search search = new SimulAnnealWeighted(constructWeightedObjective(
				constraint, mDataset), startTemp, Config.FINAL_TEMP,
				Config.ALPHA_TEMP, Config.BEST_ENERGY,
				Config.INIT_STRATEGY, Config.IND_NORM_STRAT);
		
		return search;
	}
	
	private Search initFlexibleSimulAnneal(MasterDataset mDataset, Constraint constraint) {
		Pair<Objective, Set<Objective>> obj = constructEpsFlexibleObjectiveEVP(
				constraint, mDataset);
		double startTemp = Config.START_TEMP_EVP * (double) (1d);
		Search search = new SimulAnnealEpsFlexible(obj.getO1(), obj.getO2(),
				startTemp, Config.FINAL_TEMP_EVP, Config.ALPHA_TEMP_EVP,
				Config.BEST_ENERGY_EVP, Config.INIT_STRATEGY,
				Config.IND_NORM_STRAT);
		
		return search;
	}
	
	private Search initLexicalSimulAnneal(MasterDataset mDataset, Constraint constraint) {
		double startTemp = Config.START_TEMP_EVP * (double) (1d);
		Pair<Objective, Set<Objective>> obj = constructEpsDynamicObjective(
				constraint, mDataset);
		search = new SimulAnnealEpsDynamic(obj.getO1(), obj.getO2(),
				startTemp, Config.FINAL_TEMP, Config.ALPHA_TEMP,
				Config.BEST_ENERGY, Config.INIT_STRATEGY,
				Config.IND_NORM_STRAT);
		
		return search;
	}
	
	private Search initDynamicSimulAnneal(MasterDataset mDataset, Constraint constraint) {
		double startTemp = Config.START_TEMP_EVP * (double) (1d);
		Search search = new SimulAnnealEpsLex(constructEpsLexObjective(constraint,
				mDataset), startTemp, Config.FINAL_TEMP, Config.ALPHA_TEMP,
				Config.BEST_ENERGY, Config.INIT_STRATEGY,
				Config.IND_NORM_STRAT);
		
		return search;
	}
	
	private Pair<Objective, Set<Objective>> constructEpsObjective(Constraint constraint, MasterDataset mDataset) {
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
	
	private Pair<Objective, Set<Objective>> constructEpsFlexibleObjectiveEVP(
			Constraint constraint, MasterDataset mDataset) {
		InfoContentTable table = datasetService.calcInfoContentTable(constraint, mDataset);

		Objective pvtFn = new PrivacyObjective(0d, 0d, true, constraint, table);
		Objective cleanFn = new CustomCleaningObjective(
				Config.EPSILON_FLEX_CLEANING, 0d, true, constraint, table);
		Objective changesFn = new ChangesObjective(Config.EPSILON_FLEX_SIZE,
				0d, true, constraint, table);
		Set<Objective> constraintFns = new HashSet<Objective>();
		constraintFns.add(cleanFn);
		constraintFns.add(changesFn);

		Pair<Objective, Set<Objective>> p = new Pair<Objective, Set<Objective>>();
		p.setO1(pvtFn);
		p.setO2(constraintFns);

		return p;
	}
	
	private List<Objective> constructWeightedObjective(Constraint constraint,
			MasterDataset mDataset) {
		InfoContentTable table = datasetService.calcInfoContentTable(constraint, mDataset);
		List<Objective> weightedFns = new ArrayList<Objective>();
		Objective pvtFn = new PrivacyObjective(0d, Config.ALPHA_PVT, true,
				constraint, table);
		Objective indFn = new CustomCleaningObjective(0d, Config.BETA_IND,
				true, constraint, table);
		Objective changesFn = new ChangesObjective(0d, Config.GAMMA_SIZE, true,
				constraint, table);
		weightedFns.add(pvtFn);
		weightedFns.add(indFn);
		weightedFns.add(changesFn);

		return weightedFns;
	}
	
	private List<Objective> constructEpsLexObjective(Constraint constraint,MasterDataset mDataset) {
		InfoContentTable table = datasetService.calcInfoContentTable(constraint, mDataset);
		Objective pvtFn = new PrivacyObjective(Config.EPSILON_LEX_PVT, 0d,
				true, constraint, table);
		Objective cleanFn = new CustomCleaningObjective(
				Config.EPSILON_LEX_CLEANING, 0d, true, constraint, table);
		Objective changesFn = new ChangesObjective(0d, 0d, true, constraint,
				table);

		List<Objective> fns = new ArrayList<Objective>();
		fns.add(pvtFn);
		fns.add(cleanFn);
		fns.add(changesFn);

		return fns;
	}
	
	private Pair<Objective, Set<Objective>> constructEpsDynamicObjective(Constraint constraint, MasterDataset mDataset) {
		InfoContentTable table = datasetService.calcInfoContentTable(constraint, mDataset);
		Objective pvtFn = new PrivacyObjective(0d, 0d, false, constraint, table);
		Objective cleanFn = new CustomCleaningObjective(
				Config.EPSILON_PREV_CLEANING, 0d, false, constraint, table);
		Objective changesFn = new ChangesObjective(Config.EPSILON_PREV_SIZE,
				0d, false, constraint, table);
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
		
		search = initSimulAnneal(mDataset, constraint);
		
		Set<Candidate> candidates = getRecommendations (constraint, matches, search, tgtDataset, mDataset, true);
		System.out.println(candidates);
	}
	
	public String runDataCleaning (TargetDataset tgtDataset, MasterDataset mDataset, float simThreshold, SearchType searchType) {
		String result = "";
		StringBuilder sb = new StringBuilder(result);
		
		List<Constraint> constraints = tgtDataset.getConstraints();
		System.out.println("--------------Data Cleaning------------------");
		System.out.println("Target Dataset: " + tgtDataset.toString());
		System.out.println("Master Dataset: " + mDataset.toString());
		System.out.println("simThreshold: " + simThreshold);
		System.out.println("searchType: " + searchType.toString());
		
		for (Constraint constraint: constraints) {
			System.out.println("--------------------------------------------");
			System.out.println("Constraint: " + constraint.toString());
			
			sb.append("Constraint: " + constraint.toString() + "\n");
			
			Violations vios = findVios(tgtDataset, constraint);
			System.out.println("Violations: " + vios);
			
			List<Record> vioRecs = getVioRecords(tgtDataset, vios);
			System.out.println("Violation Records: " + vioRecs);
			
			List<Match> matches = getMatching(constraint, vioRecs, mDataset.getRecords(), simThreshold, 
					tgtDataset.getName(), mDataset.getName());
			System.out.println("Matching: " + matches);
			
			if (searchType == SearchType.SA_WEIGHTED) {
				search = initWeightedSimulAnneal(mDataset, constraint);
			}
			else if (searchType == SearchType.SA_EPS_DYNAMIC) {
				search = initDynamicSimulAnneal(mDataset, constraint);
			}
			else if (searchType == SearchType.SA_EPS_LEX) {
				search = initLexicalSimulAnneal(mDataset, constraint);
			}
			else if (searchType == SearchType.SA_EPS_FLEX) {
				search = initFlexibleSimulAnneal(mDataset, constraint);
			}
			else {
				search = initSimulAnneal(mDataset, constraint);
			}
			
			Set<Candidate> candidates = getRecommendations (constraint, matches, search, tgtDataset, mDataset, true);
			System.out.println(candidates.toString());
			
			sb.append(candidates.toString() + "\n");
			sb.append("\n");
		}
		
		result = sb.toString();
		return result;
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
		
//		dcu.runDataCleaning(target, master, target.getConstraints().get(0));
		dcu.runDataCleaning(target, master, 0.8f, SearchType.SA_WEIGHTED);
	}
	
}
