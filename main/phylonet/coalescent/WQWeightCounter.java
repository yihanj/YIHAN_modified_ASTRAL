package phylonet.coalescent;

import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import phylonet.app.tool.inferst.InferenceMethod;
import phylonet.coalescent.GlobalMaps.TaxonIdentifier;
import phylonet.coalescent.Tripartition;
import phylonet.lca.SchieberVishkinLCA;
import phylonet.tree.model.TNode;
import phylonet.tree.model.Tree;
import phylonet.tree.model.sti.STINode;
import phylonet.tree.model.sti.STITreeCluster;
import phylonet.util.BitSet;

public class WQWeightCounter extends Counter<Tripartition> {

	String[] gtTaxa;
	String[] stTaxa;

	// private List<Set<Tripartition>> X;

	// private Map<Tripartition, Integer> geneTreeTripartitonCount;
	private Map<STITreeCluster,Map<Tripartition, Integer>> geneTreeTripartitonCount;
	
	//private Map<AbstractMap.SimpleEntry<STITreeCluster, STITreeCluster>, Integer> geneTreeInvalidSTBCont;

	private boolean rooted;


	public WQWeightCounter(String[] gtTaxa, String[] stTaxa,
			boolean rooted, WQClusterCollection clusters) {
		this.gtTaxa = gtTaxa;
		this.stTaxa = stTaxa;
		this.rooted = rooted;
		this.clusters = clusters;
	}
	
	void traverseTrees(List<Tree> trees, boolean fromGeneTrees, int n) {
		
		for (Tree tr : trees) {
	
			Map<TNode, STITreeCluster> nodeToSTCluster = new HashMap<TNode, STITreeCluster>(n);
	
			for (TNode node : tr.postTraverse()) {				
				// System.err.println("Node is:" + node);
				if (node.isLeaf()) {
					String nodeName = getSpeciesName(node.getName());
					
					STITreeCluster cluster = new STITreeCluster();
					cluster.addLeaf(GlobalMaps.taxonIdentifier.taxonId(nodeName));

					addToClusters(cluster, 1);
					
					addToClusters(cluster.complementaryCluster(), n - 1);

					nodeToSTCluster.put(node, cluster);

					// nodeToGTCluster.put(node, gtCluster);

				} else {
					int childCount = node.getChildCount();
					
					if (childCount >3 || (childCount == 3 && node != tr.getRoot()) ) {
						throw new RuntimeException(
								"None bifurcating tree: " + tr + "\n"
										+ node);
					}
					STITreeCluster childbslist[] = new STITreeCluster[childCount];
					BitSet bs = new BitSet(stTaxa.length);
					int index = 0;
					for (TNode child: node.getChildren()) {
						childbslist[index++] = nodeToSTCluster.get(child);
						bs.or(nodeToSTCluster.get(child).getBitSet());
					}

					STITreeCluster cluster = new STITreeCluster();
					cluster.setCluster((BitSet) bs.clone());

					int size = cluster.getClusterSize();

					addToClusters(cluster, size);
					nodeToSTCluster.put(node, cluster);
					
					STITreeCluster remaining = cluster.complementaryCluster();
					
					if (size != n) {
						addToClusters(remaining, n - size);
					}

					if (childCount == 2 ) {
						if (size != n) {
							tryAddingTripartition( childbslist[0],  childbslist[1], remaining, node, fromGeneTrees);
						}
					} else if (childCount == 3) {

						tryAddingTripartition(childbslist[0], childbslist[1], childbslist[2] , node, fromGeneTrees);

					} else {
						throw new RuntimeException("hmmm?");
						/*
						 * if (childCount == 2) { STITreeCluster l_cluster =
						 * childbslist[0];
						 * 
						 * STITreeCluster r_cluster = childbslist[1];
						 * 
						 * STITreeCluster allMinuslAndr_cluster =
						 * treeComplementary(null this should be
						 * gtCluster?,leaves);
						 * 
						 * STITreeCluster lAndr_cluster = cluster;
						 * 
						 * if (allMinuslAndr_cluster.getClusterSize() != 0) { //
						 * add Vertex STBs tryAddingSTB(l_cluster, r_cluster,
						 * cluster, node, true); tryAddingSTB( r_cluster,
						 * allMinuslAndr_cluster, null, node, true);
						 * tryAddingSTB(l_cluster, allMinuslAndr_cluster, null,
						 * node, true);
						 * 
						 * // Add the Edge STB tryAddingSTB(lAndr_cluster,
						 * allMinuslAndr_cluster, null, node, true); }
						 * 
						 * } else if (childCount == 3 && node.isRoot()) {
						 * STITreeCluster l_cluster = childbslist[0];
						 * 
						 * STITreeCluster m_cluster = childbslist[1];
						 * 
						 * STITreeCluster r_cluster = childbslist[2];
						 * 
						 * tryAddingSTB(l_cluster, r_cluster, null, node, true);
						 * tryAddingSTB(r_cluster, m_cluster, null, node, true);
						 * tryAddingSTB(l_cluster, m_cluster, null, node, true);
						 * } else { throw new
						 * RuntimeException("None bifurcating tree: "+ tr+ "\n"
						 * + node); }
						 */}
				}
			}

		}

	}

	public void computeTreePartitions(Inference<Tripartition> inference) {

		int k = inference.trees.size();
		int n = stTaxa.length;

		geneTreeTripartitonCount = new HashMap<STITreeCluster, Map<Tripartition,Integer>>();
		//geneTreeInvalidSTBCont = new HashMap<AbstractMap.SimpleEntry<STITreeCluster, STITreeCluster>, Integer>();
		// geneTreeRootSTBs = new HashMap<Tripartition, Integer>(k*n);
		// needed for fast version
		// clusterToSTBs = new HashMap<STITreeCluster, Set<Tripartition>>(k*n);

		STITreeCluster all = new STITreeCluster();
		all.getBitSet().set(0, n);
		addToClusters(all, stTaxa.length);

		
		traverseTrees(inference.trees, true, n);
		
		int s = 0;
		for (Map<Tripartition, Integer> m : geneTreeTripartitonCount.values()) {
			for (Integer c : m.values()) {
				s += c;
			}
		}
		System.err.println("Tripartitons in gene trees (count): "
				+ geneTreeTripartitonCount.size());
		System.err.println("Tripartitons in gene trees (sum): " + s);

		s = clusters.getClusterCount();

		System.err.println("Number of Clusters: " + s);

		weights = new HashMap<Tripartition, Integer>();
		// System.err.println("sigma n is "+sigmaN);

	}
	
	private Map<Tripartition, Integer> getSetForCluster(STITreeCluster c) {
		Map<Tripartition, Integer> s = geneTreeTripartitonCount.get(c);
		if (s == null) {
			s = new HashMap<Tripartition, Integer>();
			geneTreeTripartitonCount.put(c, s);
		}
		return s;
	}

	Integer getTripartitionCount(Tripartition t) {
		Integer a = getSetForCluster(t.cluster1).get(t);
		return a == null ? 0 : a;
	}
	
	void setTripartitonCount(Tripartition t, Integer c) {
		getSetForCluster(t.cluster1).put(t, c);
	}
	
	public void addExtraBipartitionsByInput(ClusterCollection extraClusters,
			List<Tree> trees, boolean extraTreeRooted) {

		traverseTrees(trees, false, stTaxa.length);
		int s = extraClusters.getClusterCount();
		/*
		 * for (Integer c: clusters2.keySet()){ s += clusters2.get(c).size(); }
		 */
		System.err
				.println("Number of Clusters After additions from extra Trees: "
						+ s);
	}

	private void tryAddingTripartition(STITreeCluster l_cluster,
			STITreeCluster r_cluster, STITreeCluster remaining, TNode node,
			boolean fromGeneTrees) {
		// System.err.println("before adding: " + STBCountInGeneTrees);
		// System.err.println("Trying: " + l_cluster + "|" + r_cluster);
		//int size = cluster.getClusterSize();
		
		//if (l_cluster.isDisjoint(r_cluster)) {
		Tripartition trip = new Tripartition(l_cluster, r_cluster, remaining);
		((STINode) node).setData(trip);
		if (fromGeneTrees) {
			setTripartitonCount(trip , getTripartitionCount(trip) + 1);
		}

		/*
		 * if (size == allInducedByGTSize){ if (!
		 * geneTreeRootSTBs.containsKey(stb)) { geneTreeRootSTBs.put(stb,
		 * 1); } else { geneTreeRootSTBs.put(stb,
		 * geneTreeRootSTBs.get(stb)+1); } }
		 */
		/*} else {
			AbstractMap.SimpleEntry<STITreeCluster, STITreeCluster> stb = 
					l_cluster.getBitSet().cardinality() > r_cluster.getBitSet().cardinality() ? 
					new AbstractMap.SimpleEntry<STITreeCluster, STITreeCluster>(l_cluster, r_cluster) : 
					new AbstractMap.SimpleEntry<STITreeCluster, STITreeCluster>(r_cluster, l_cluster);
			geneTreeInvalidSTBCont.put(stb,	geneTreeInvalidSTBCont.containsKey(stb) ? 
					geneTreeInvalidSTBCont.get(stb) + 1 : 
					1);
			// System.err.println("Adding only to extra");
			// This case could happen for multiple-copy
			BitSet and = (BitSet) l_cluster.getBitSet().clone();
			and.and(r_cluster.getBitSet());

			BitSet l_Minus_r = (BitSet) and.clone();
			l_Minus_r.xor(l_cluster.getBitSet());
			STITreeCluster lmr = new STITreeCluster();
			lmr.setCluster(l_Minus_r);

			BitSet r_Minus_l = (BitSet) and.clone();
			r_Minus_l.xor(r_cluster.getBitSet());
			STITreeCluster rml = new STITreeCluster();
			rml.setCluster(r_Minus_l);

			if (!rml.getBitSet().isEmpty()) {
				addToClusters(rml, rml.getClusterSize(), false);
				// addSTBToX( new Tripartition(l_cluster, rml, cluster),size);
			}
			if (!lmr.getBitSet().isEmpty()) {
				addToClusters(lmr, lmr.getClusterSize(), false);
				// addSTBToX(new Tripartition(lmr, r_cluster, cluster), size);
			}
		}*/
	}

	// static public int cnt = 0;

	public void preCalculateWeights(List<Tree> trees, List<Tree> extraTrees) {
		

	}

	

	class QuartetWeightTask implements CalculateWeightTask{

		/**
		 * 
		 */
		private Tripartition trip;
	
		public QuartetWeightTask(Tripartition trip) {
			this.trip = trip;
		}

		int calculateMissingWeight() {
			// System.err.print("Calculating weight for: " + biggerSTB);
			int weight = 0;
			for (Entry<STITreeCluster, Map<Tripartition, Integer>> s : geneTreeTripartitonCount.entrySet()) {
				//System.err.println(s.getValue().size());
				int I0 = trip.cluster1.getBitSet().intersectionSize(s.getKey().getBitSet()),
					I3 = trip.cluster2.getBitSet().intersectionSize(s.getKey().getBitSet()),
					I6 = trip.cluster3.getBitSet().intersectionSize(s.getKey().getBitSet());
				for (Entry<Tripartition, Integer> otherTrip : s.getValue().entrySet()) {
					weight += trip.sharedQuartetCount(otherTrip.getKey(),I0,I3, I6) * otherTrip.getValue();
				}
			}
			weights.put(trip, weight);
			if (weights.size() % 100000 == 0)
				System.err.println("Calculated "+weights.size()+" weights");
			return weight;
		}

		public Integer compute() {
			return calculateMissingWeight();
		}

	}



	@Override
	public CalculateWeightTask getWeightCalculateTask(Tripartition t) {
		return new QuartetWeightTask(t);
		
	}
}
