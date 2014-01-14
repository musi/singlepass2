package netflix.algorithms.memorybased.GraySheepUsers;


import java.util.*;
import netflix.memreader.*;
import netflix.utilities.*;
import cern.colt.list.*;
import cern.colt.map.*;

/************************************************************************************************/
public class SinglePass
{
	private MemHelper 					helper;
	private	int 						callNo;					 
	private Timer227 							timer;    

	private ArrayList<IntArrayList> 	finalClusters;
	private OpenIntIntHashMap 			uidToCluster;

	private ArrayList<IntArrayList> 	finalSpheres;
	private OpenIntIntHashMap 			uidToSphere;

	private ArrayList<Centroid> 				centroids;
	private ArrayList<Centroid> 				newCentroids;
	private ArrayList<Centroid> 				sphereCentroids;

	private OpenIntIntHashMap   				clusterMap;
	private OpenIntIntHashMap   				sphereMap;
	private int									simVersion;

	/************************************************************************************************/

	/**
	 * Builds the RecTree and saves the resulting clusters.
	 */

	public SinglePass(MemHelper helper)    
	{
		this.helper   	= helper;
		this.finalClusters 	= new ArrayList<IntArrayList>(); //Creates ArrayList with initial default capacity 10.
		this.uidToCluster  	= new OpenIntIntHashMap();       // <E> is for an element in the arraylist
		this.clusterMap 	  	= new OpenIntIntHashMap();
		this.sphereMap 	  	= new OpenIntIntHashMap();

		this.finalSpheres 	= new ArrayList<IntArrayList>(); //Creates ArrayList with initial default capacity 10.
		this.uidToSphere  	= new OpenIntIntHashMap();

		this.centroids 		= new ArrayList<Centroid> ();
		this.newCentroids 	= new ArrayList<Centroid> ();
		this.sphereCentroids = new ArrayList<Centroid> ();
		this.callNo	  		= 0;
		this.timer  	  		= new Timer227();
	}

	/************************************************************************************************/ 
	//  This is called after the constructor call  
	public int cluster(int call, int sVersion)    
	{
		callNo			= call;
		simVersion		= sVersion;	

		//    	final spheres that will be created .........

		finalSpheres 	= constructRecTreeSphere(helper.getListOfUsers(),  
													helper.getGlobalAverage());

		//-------------------
		// Make map
		//-------------------
		//This is basically to make a map, a particular user is in which sphere

		IntArrayList sphere = null;

		for(int i = 0; i < finalSpheres.size(); i++)
		{   	
			sphere = finalSpheres.get(i);       
			for(int j = 0; j < sphere.size(); j++)			//a sphere is a collection of users, go through this             
			{ 
				uidToSphere.put(sphere.get(j), i);
			}
		}

		//Print spheres
		for (int t=1; t<=finalSpheres.size(); t++)
		{
			System.out.print(finalSpheres.get(t-1).size()+", ");
		}

		return finalSpheres.size();

	}

	/************************************************************************************************/
	/**
	 * Gets the specified cluster by its positional id. 
	 * @return  The cluster at location id in the clusters list.
	 */  
	public IntArrayList getClusterByID(int id)    
	{
		return finalClusters.get(id);
	}
	/************************************************************************************************/
	/**
	 * Gets the specified sphere by its positional id. 
	 * @return  The sphere at location id in the sphere list.
	 */ 
	public IntArrayList getSphereByID(int id)    
	{
		return finalSpheres.get(id);
	}

	/************************************************************************************************/
	/**
	 * Gets the id for the cluster containing the specified
	 * user. 
	 * @return  The location of the cluster containing
	 *          the specified uid in the clusters list. 
	 */
	public int getClusterIDByUID(int uid)    
	{
		return uidToCluster.get(uid);	//it will return the index of a single cluster within many (which are stored by index wise)
	}

	/************************************************************************************************/
	/**
	 * Gets the id for the sphere containing the specified
	 * user. 
	 * @return  The location of the sphere containing
	 *          the specified uid in the sphere list. 
	 */

	//not usefull for spheres.....
	public  int getSphereIDByUID(int uid)    
	{
		return uidToSphere.get(uid);  	//it will return the index of a single sphere within many (which are stored by index wise)
	}

	/************************************************************************************************/
	/**
	 * Gets the id for the spheres in whose radious uid exist.... 
	 *      * @return  The location of the spheres containing
	 *          the specified uid in the sphere list. 
	 */

	//used in recommendation for getting the id of cluster 
	public int getSpheresIDByUID(int uid)    
	{

		double distance 				=-1;
		//		Radius of spheres...
		double radious;
		int m							= 0;
		boolean insphere				=false;
		OpenIntIntHashMap sphereCount	= new OpenIntIntHashMap();
		OpenIntDoubleHashMap sphereDis	= new OpenIntDoubleHashMap();
		double	avg						=0.0;

		for(int i = 0; i < sphereCentroids.size(); i++)        
		{
			avg		=sphereCentroids.get(i).getSphereAverage();
			//          if(simVersion==1)
			radious= sphereCentroids.get(i).getRadious(i);
			distance = sphereCentroids.get(i).distanceWithDefaultSphere(uid, avg, helper);
			//          else if(simVersion==2)
			//          distance = centroids.get(i).distanceWithoutDefault(uid, cliqueAverage, helper);
			//          else if(simVersion==3)
			//          distance = centroids.get(i).distanceWithDefaultVS(uid, cliqueAverage, helper);
			//          else if(simVersion==4)
			//          distance = centroids.get(i).distanceWithoutDefaultVS(uid, cliqueAverage, helper);
			//          else if(simVersion==5)
			//          distance = centroids.get(i).distanceWithPCC(uid, i, helper);   	 
			//          else if(simVersion==6)
			//          distance = centroids.get(i).distanceWithVS(uid, i, helper);              	 

			//Distance is the sim, which should be MAXIMUM for a Good cluster
			// i.e. a new user will be assigned to the cluster with whom it got the highest sim  

			if (radious < distance)
			{
				sphereCount.put(i, finalSpheres.get(i).size());
				insphere=true;
			}           

		}


		//If not similar to any sphere then find the sphere with whom it is most similar..... 
		if (insphere == false) 
		{ 
			for(int i = 0; i < sphereCentroids.size(); i++)        
			{
				avg		=sphereCentroids.get(i).getSphereAverage();
				distance = sphereCentroids.get(i).distanceWithDefaultSphere(uid, avg, helper);
				sphereDis.put(i, distance);
			}
			IntArrayList keys= sphereDis.keys();
			DoubleArrayList values=sphereDis.values();
			sphereDis.pairsSortedByValue(keys, values);
			m=keys.get(sphereDis.size()-1);
		}
		else
		{
			IntArrayList keys= sphereCount.keys();
			IntArrayList values=sphereCount.values();
			sphereCount.pairsSortedByValue(keys, values);
			m=keys.get(sphereCount.size()-1);

		}
		return m;
	}
	/************************************************************************************************/
	/**
	 * Gets the size of the cluster by ID
	 * @return  The size of the cluster 
	 * */   
	public int getClusterSizeByID(int id)    
	{
		return finalClusters.size();
	}
	/************************************************************************************************/
	/**
	 * Gets the size of the cluster by ID
	 * @return  The size of the cluster 
	 * */   
	public int getSphereSizeByID(int id)    
	{
		return finalSpheres.size();
	}

	/************************************************************************************************/ 
	/**
	 * Gets the cluster containing the specified user. 
	 * @return  The cluster containing the speficied user. 
	 */

	public IntArrayList getClusterByUID(int uid)    
	{
		return finalClusters.get(uidToCluster.get(uid));	//it return the cluster
	}

	/************************************************************************************************/   
	/**
	 * Gets the cluster containing the specified user. 
	 * @return  The cluster containing the speficied user. 
	 */
	public IntArrayList getSphereByUID(int uid)    
	{	
		return finalSpheres.get(uidToSphere.get(uid));	//it return the cluster
	}

	/************************************************************************************************/
	//     create the spheres....
	public ArrayList<IntArrayList> constructRecTreeSphere(IntArrayList dataset,    // helper.getListOfUsers(),  
			double cliqueAverage)    // helper.getGlobalAverage());                                              
			{
		ArrayList<IntArrayList> spheres = new ArrayList<IntArrayList>();

		SphereCollection subSpheres = nSphere (dataset, 	 
				cliqueAverage);



		for(int i = 0; i < subSpheres.size(); i++)       
		{
			spheres.add(subSpheres.getSphere(i));  
		}

		return spheres;
			}

	/*********************************************************************************************************/
	/**
	 * KMean: Make K clusters
	 */
	//It returns the cluster collection object
	/**
	 * @param dataset
	 * @param k
	 * @param cliqueAverage
	 * @return
	 */
	public SphereCollection nSphere(IntArrayList dataset, 		//all users in the database					
									double cliqueAverage) 		//golbal average in the database    
	{

		//    	They will be initialized for every call
		int  newSphere[] 	=new int[100];				// id of spheres which all can contain point
		int point;    

		for(int i = 1; i < dataset.size(); i++)            
		{
			point = dataset.get(i);  						    // a uid             
			//  		as for first time call point will become sphere......
			if(callNo==1) 
			{    	              
				sphereCentroids.add( new Centroid (point,helper));             
				callNo++;

			}
			//  		if it is not first call then we will check similar spheres to the uid.....
			else
			{
				// after each iteration it will be cleared as null.....
				for(int n=0; n<newSphere.length;n++)
				{
					newSphere[n]=-1;
				}
				newSphere = findClosestSphere(point, 			//This array is list of sphere index closest to this point ) 	
						sphereCentroids, 
						cliqueAverage);
			}

			//----------------------------------------------------------

			//add the point to the appropriate sphere, and update
			//the new version of that sphere centroid. 
			//Infect..... This point is not in the sphereMap (which contains point-to-cluster mapping)	
			//So what we do, is to add this point to this sphereMap and in sphereCentroids 's sphere 
			//(if it is not a starting point)
			//----------------------------------------------------------
			for (int j=0 ; j<newSphere.length ; j++)
			{
				if(newSphere[j]!=-1)
				{
					if(!sphereMap.containsKey(point))     				//update the point to clusterMap           
					{
						sphereMap.put(point, newSphere[j]); 		
						//    					If the centroid was initialised to this point, we don't want to add it again. 
						if (sphereCentroids.get(newSphere[j]).startingUid != point)  //update the point to sphereCentroid 
						{
							sphereCentroids.get(newSphere[j]).addPointSphere(point, helper);	
						}

					}

					//----------------------------------------------------------
					//The point has changed clusters. We add the
					//point to the new cluster and modify the centroid 
					//Infect....Here point is already there in the clusterMap, so we check that if this mapping has been changed or not
					//Mapping = (point, sphereId), If this has been changed in the current iteration (point is given a new sphere)
					//then we have to do two things: Update sphereMap (point, new cluster) by deleting the previous map and mapping the new one
					// and second, update this point in the sphereCentroid (Mean add this point to the new cluster as well)
					//----------------------------------------------------------

					else if(sphereMap.get(point) != newSphere[j])        // this is because, this while is called multiple times        
					{
						sphereCentroids.get(sphereMap.get(point)).removePointSphere(point, helper);
						sphereCentroids.get(newSphere[j]).addPointSphereWithRadious(point,newSphere[j], helper);
						
						sphereMap.put(point, newSphere[j]);
					}
				}
			} //end of for, where we put all the points in some sphere
		}
		//----------------------------------------------------------

		//TEMP: Goes through every point and finds the total distance
		//to the centroids. If everything is working correctly, this 
		//number should never increase. 

		double 	simlair = 0.0;
		int 	tempCluster;
		double	avg			=0.0;
		for(int i = 0; i < sphereCentroids.size(); i++)					//Compute for all centroids            
		{
			sphereCentroids.get(i).findSphereAverage();				//compute average ratings in a centoid 
		}

		//As In the previous for, all points has been assigned to their respective spheres (depends on the distance)
		//So we can go through these points, find their particular sphere (by sphereMap) and then can compute 
		//the total distance from this point to that centroid.
		//????......WE should not take into account the point for which we are taking the computing this distance
		//          in the distance computation function? (it is because this point is already there in the centroid)

		for(int i=0; i < dataset.size(); i++)           
		{
			point = dataset.get(i);
			tempCluster =  sphereMap.get(point);
			avg			=sphereCentroids.get(tempCluster).getSphereAverage();

			if(simVersion==1)
				simlair +=   sphereCentroids.get(tempCluster).distanceWithDefaultSphere(point, avg, helper);
			else if(simVersion==2)
				simlair +=   sphereCentroids.get(tempCluster).distanceWithoutDefaultSphere(point, avg, helper);
			else if(simVersion==3)
				simlair +=   sphereCentroids.get(tempCluster).distanceWithDefaultVSSphere(point, avg, helper);
			else if(simVersion==4)
				simlair +=  sphereCentroids.get(tempCluster).distanceWithoutDefaultVSSphere(point, avg, helper);
			else if(simVersion==5)
				simlair +=   sphereCentroids.get(tempCluster).distanceWithPCCSphere(point, i, helper);   	 
			else if(simVersion==6)
				simlair +=   sphereCentroids.get(tempCluster).distanceWithVSSphere(point, i, helper);


		} 
		System.out.println( "Similarity = " + simlair);

		SphereCollection spheres = new SphereCollection(sphereCentroids.size(),helper);
		sphereMap.forEachPair(spheres); 				// (This calls the apply over-rided function in the cluster collection class)
		return spheres;
	}
	/*********************************************************************************************/
	/**
	 * @param  uid  The user to find a centroid for.
	 * @param  centroids  The list of centroids. 
	 * @retrun The index of the closest centroid to uid. 
	 */
	//     find set of spheres closet to the point....

	private int [] findClosestSphere(int uid, 
			ArrayList<Centroid> centroids, 
			double cliqueAverage)     
	{
		double distance 	= 1;
		int minIndex []		= new int[150];
		//double threshold 	= 0.09;
		double radious;
		int m				= 0;
		boolean insphere	=false;
		double avg			=0.0;

		for(int n=0; n<minIndex.length;n++)
		{
			minIndex[n]=-1;
		}

		for(int i = 0; i < centroids.size(); i++)        
		{         
			avg		=centroids.get(i).getSphereAverage();
			radious	=centroids.get(i).getRadious(i);
			//     if(simVersion==1)
			distance = centroids.get(i).distanceWithDefaultSphere(uid,avg, helper);
			//        	else if(simVersion==2)
			//        		distance = centroids.get(i).distanceWithoutDefault(uid, cliqueAverage, helper);
			//        	else if(simVersion==3)
			//        		distance = centroids.get(i).distanceWithDefaultVS(uid, cliqueAverage, helper);
			//        	else if(simVersion==4)
			//        		distance = centroids.get(i).distanceWithoutDefaultVS(uid, cliqueAverage, helper);
			//        	else if(simVersion==5)
			//        		distance = centroids.get(i).distanceWithPCC(uid, i, helper);   	 
			//        	else if(simVersion==6)
			//        		distance = centroids.get(i).distanceWithVS(uid, i, helper);
			//
			//        	System.out.println("distance from " + uid + " to cluster " + i + " is " + distance);

			//Distance is the sim, which should be MAXIMUM for a Good cluster
			// i.e. a new user will be assigned to the cluster with whom it got the highest sim  

			if (radious < distance)
			{
				minIndex[i] =i;
				insphere=true;
			}           

		}


		//If insphere is false then craete a new centroid 
		if (insphere == false) 
		{ 
			sphereCentroids.add( new Centroid (uid,helper));
			m = sphereCentroids.size()-1;
			minIndex [0] = m;
		}
		return minIndex;
	}
	/**********************************************************************************************/


	/*********************************************************************************************/
	/**
	 * @param int cluserId, int mid
	 * @return the rating given by this cluster to the specified movie (In-fact this is the cluster avg/all users)
	 */

	public double getRatingForAMovieInACluster (int clusterId, int mid)
	{   	
		return centroids.get(clusterId).getRating(mid);
	}


	/**********************************************************************************************/

	public double getRatingForAMovieInASphere (int clusterId, int mid)
	{   	
		return sphereCentroids.get(clusterId).getRatingSphere(mid);
	}

	/**********************************************************************************************/

	/**
	 * @param int cluserId, int mid
	 * @return the Average given by this cluster to the specified movie
	 */

	public double getAverageForAMovieInACluster (int clusterId, int mid)
	{   	
		return centroids.get(clusterId).getAverage();


	}   

	/**********************************************************************************************/

	public double getAverageForAMovieInASphere (int sphereId, int mid)
	{   	
		return sphereCentroids.get(sphereId).getSphereAverage();
	}   


	/*******************************************************************************************************/

	/**
	 * Find the sim b/w a user and other clusters (other than the one in which a user lies)
	 * @param   uid
	 * @return  Sim between user and centroid
	 */

	public double findSimWithOtherClusters(int uid, int i)
	{

		double distance =0.0;   

		if(simVersion==1)
			distance = centroids.get(i).distanceWithDefault(uid, helper.getGlobalAverage(), helper);
		else if(simVersion==2)
			distance = centroids.get(i).distanceWithoutDefault(uid, helper.getGlobalAverage(), helper);
		else if(simVersion==3)
			distance = centroids.get(i).distanceWithDefaultVS(uid, helper.getGlobalAverage(), helper);
		else if(simVersion==4)
			distance = centroids.get(i).distanceWithoutDefaultVS(uid, helper.getGlobalAverage(), helper);
		else if(simVersion==5)
			distance = centroids.get(i).distanceWithPCC(uid, i, helper);   	 
		else if(simVersion==6)
			distance = centroids.get(i).distanceWithVS(uid, i, helper);
		return distance;	 

	}


	//---------------------
	public double findSimWithOtherSphere(int uid, int i)
	{

		double distance =0.0;   

		if(simVersion==1)
			distance = sphereCentroids.get(i).distanceWithDefaultSphere(uid, helper.getGlobalAverage(), helper);
		else if(simVersion==2)
			distance = sphereCentroids.get(i).distanceWithoutDefaultSphere(uid, helper.getGlobalAverage(), helper);
		else if(simVersion==3)
			distance = sphereCentroids.get(i).distanceWithDefaultVSSphere(uid, helper.getGlobalAverage(), helper);
		else if(simVersion==4)
			distance = sphereCentroids.get(i).distanceWithoutDefaultVSSphere(uid, helper.getGlobalAverage(), helper);
		else if(simVersion==5)
			distance = sphereCentroids.get(i).distanceWithPCCSphere(uid, i, helper);   	 
		else if(simVersion==6)
			distance = sphereCentroids.get(i).distanceWithVSSphere(uid, i, helper);

		return distance;	 

	}    
	/*******************************************************************************************************/


}

