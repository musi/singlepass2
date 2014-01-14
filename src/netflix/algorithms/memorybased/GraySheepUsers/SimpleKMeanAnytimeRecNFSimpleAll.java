package netflix.algorithms.memorybased.GraySheepUsers;

import java.io.BufferedWriter;


import java.io.FileWriter;
import netflix.algorithms.memorybased.memreader.FilterAndWeight;
import netflix.memreader.*;
import netflix.recommender.ItemItemRecommender;
import netflix.rmse.RMSECalculator;
import netflix.utilities.*;
import cern.colt.list.*;
import cern.colt.map.*;

/************************************************************************************************/
public class SimpleKMeanAnytimeRecNFSimpleAll
{

	private SinglePass						singlePass;

	private int								myClasses;
	//private int								myTotalFolds;
	//Objects of some classes
	MemHelper 			trainMMh;
	MemHelper 			allHelper;
	MemHelper 			testMMh;
	MeanOrSD			MEANORSD;
	Timer227 			timer;
	FilterAndWeight		myUserBasedFilter;   //Filter and Weight, for User-based CF
	ItemItemRecommender	myItemRec;		     //item-based CF    
	MemHelper			trainSVMRegMMh;		 //SVM Regression model for the test set
	MemHelper			trainSVMClassMMh;	 //SVM Regression model for the test set
	MemHelper			trainNBMMh;			 //SVM Regression model for the test set
	MemHelper			trainKNNMMh;		 //SVM Regression model for the test set


	private int 		totalRecSamples;
	private int         kClusters;
	private int			nSpheres;
	BufferedWriter      writeData1;
	BufferedWriter      writeData2;

	private String      myPath;
	private int         totalNan=0;
	private int         totalNegatives=0;
	private int			simVersion;

	//Related to finding the gray sheep user's predictions
	private int			graySheepUsers;							// total gray sheep users
	private int			graySheepSamples;						// total gray sheep predictions
	private int			powerUsersThreshold;					// power user size
	private double		simThreshold;							//
	private int			numberOfneighbours;						// no. of neighbouring cluster for an active user

	private int			totalIterations;						//no. of iterations required in the KMeans clustering 

	//Regarding Results
	double 								MAE;
	double								MAEPerUser;
	double 								RMSE;
	double 								RMSEPerUser;
	double								Roc;
	double								coverage;
	double								pValue;
	double								kMeanEigen_Nmae;
	double								kMeanCluster_Nmae;

	//SD in one fold or when we do hold-out like 20-80
	double								SDInMAE;
	double								SDInROC;
	double 								SDInTopN_Precision[];
	double 								SDInTopN_Recall[];
	double 								SDInTopN_F1[];	

	double            					precision[];		//evaluations   
	double              				recall[];   
	double              				F1[];    
	private OpenIntDoubleHashMap 		midToPredictions;	//will be used for top_n metrics (pred*100, actual)

	//1: fold, 2: k, 3:dim
	double              array_MAE[][];	      			// [gsu][fold]
	double              array_MAEPerUser[][];
	double              array_NMAE[][];
	double              array_NMAEPerUser[][];
	double              array_RMSE[][];
	double              array_RMSEPerUser[][];
	double              array_Coverage[][];
	double              array_ROC[][];
	double              array_BuildTime[][];
	double				array_GSUSamples[][];			//gs users found in [gsu][all folds]
	double				array_GSU[][];					//gs samples found in [gsu][all folds]


	double              array_Precision[][][]; 		   //[topnN][gsu][fold]
	double              array_Recall[][][];
	double              array_F1[][][];    

	//will store the grid results in the form of mean and sd
	double				gridResults_Mean_MAE[];
	double				gridResults_Mean_MAEPerUser[];
	double				gridResults_Mean_NMAE[];
	double				gridResults_Mean_NMAEPerUser[];
	double				gridResults_Mean_RMSE[];
	double				gridResults_Mean_RMSEPerUser[];
	double				gridResults_Mean_ROC[];
	double				gridResults_Mean_GSU[];
	double				gridResults_Mean_GSUSamples[];		//GSU and Samples			

	double				gridResults_Mean_Precision[][];   	//[TOPn][][]
	double				gridResults_Mean_Recall[][];
	double				gridResults_Mean_F1[][];
	double				gridResults_Mean_Coverage[];

	double				gridResults_Sd_MAE[];
	double				gridResults_Sd_MAEPerUser[];
	double				gridResults_Sd_NMAE[];
	double				gridResults_Sd_NMAEPerUser[];
	double				gridResults_Sd_RMSE[];
	double				gridResults_Sd_RMSEPerUser[];
	double				gridResults_Sd_ROC[];
	double				gridResults_sd_GSU[];
	double				gridResults_sd_GSUSamples[];		//GSU and Samples			

	double				gridResults_Sd_Precision[][];
	double				gridResults_Sd_Recall[][];
	double				gridResults_Sd_F1[][];
	double				gridResults_Sd_Coverage[];

	double              mean_MAE[];	      					// Means of results, got from diff folds
	double              mean_MAEPerUser[];
	double              mean_NMAE[];						// for each version
	double              mean_NMAEPerUser[];
	double              mean_RMSE[];
	double              mean_RMSEPerUser[];
	double              mean_Coverage[];
	double              mean_ROC[];
	double              mean_BuildTime[];
	double              mean_Precision[][];   
	double              mean_Recall[][];   
	double              mean_F1[][];       

	double              sd_MAE[];		      					// SD of results, got from diff folds
	double              sd_MAEPerUser[];
	double              sd_NMAE[];								// for each version
	double              sd_NMAEPerUser[];
	double              sd_RMSE[];
	double              sd_RMSEPerUser[];
	double              sd_Coverage[];
	double              sd_ROC[];
	double              sd_BuildTime[];
	double              sd_Precision[][];   
	double              sd_Recall[][];   
	double              sd_F1[][];   


	int 				myFlg =1; //done by me..........
	int 				currentFold;
	IntArrayList		myCentroids1, myCentroids2,myCentroids3,myCentroids4,myCentroids5;
	private static int  MAX_NEAREST_NEIGHBOURS = 70;
	private static int  MIN_NEIGHBOURS = 5;
	private static int  NEIGHBOURS_INCREMENT = 10;
	

	/************************************************************************************************/

	public SimpleKMeanAnytimeRecNFSimpleAll()    
	{

		totalRecSamples 	= 0;
		myClasses			= 5;
		simVersion			= 1; 	//1=PCCwithDefault, 2=PCCwithoutDefault
									//3=VSWithDefault,  4=VSWithDefault
									//5=PCC, 			  6=VS

		timer 				 = new Timer227();
		MEANORSD			 = new MeanOrSD();

		MAE 				= 0;
		MAEPerUser			= 0;
		RMSE 				= 0;
		RMSEPerUser 		= 0;
		Roc 				= 0;
		coverage			= 0;
		SDInMAE				= 0;
		SDInROC				= 0;
		SDInTopN_Precision	= new double[8];
		SDInTopN_Recall		= new double[8];
		SDInTopN_F1			= new double[8];

		midToPredictions    = new OpenIntDoubleHashMap();     	  
		precision    		= new double[8];		//topN; for six values of N (top5, 10, 15...30)
		recall  			= new double[8];		// Most probably we wil use top10, or top20
		F1					= new double[8];

		//Initialize results, Mean and SD	    	
		array_MAE  	 		=   new double[3][5];
		array_MAEPerUser	=   new double[3][5]; 
		array_NMAE		 	=   new double[3][5];
		array_RMSE 	 		=   new double[3][5];
		array_RMSEPerUser 	=   new double[3][5];
		array_ROC 		 	=   new double[3][5];
		
		array_Precision 	= new double[8][3][5]; //[topN][fold]
		array_Recall 	 	= new double[8][3][5];
		array_F1 		 	= new double[8][3][5];

		//So we have to print this grid result for each scheme,
		//Print in the form of "mean + sd &" 
		gridResults_Mean_MAE 			=   new double[3];	        
		gridResults_Mean_NMAE			=   new double[3];        
		gridResults_Mean_RMSE			=   new double[3];
		gridResults_Mean_MAEPerUser	=   new double[3];
		gridResults_Mean_RMSEPerUser	=   new double[3];
		gridResults_Mean_NMAEPerUser	=   new double[3];
		gridResults_Mean_ROC			=   new double[3];
		gridResults_Mean_Coverage		=   new double[3];

		gridResults_Mean_Precision		= new double[8][3]; 
		gridResults_Mean_Recall		= new double[8][3];
		gridResults_Mean_F1			= new double[8][3];       

		gridResults_Sd_MAE			= new double[3];	         
		gridResults_Sd_NMAE		= new double[3];	         
		gridResults_Sd_RMSE		= new double[3];
		gridResults_Sd_NMAEPerUser	= new double[3];
		gridResults_Sd_MAEPerUser	= new double[3];
		gridResults_Sd_RMSEPerUser = new double[3];	         
		gridResults_Sd_ROC			= new double[3];
		gridResults_Sd_Coverage	= new double[3];

		gridResults_Sd_Precision	= new double[8][3];
		gridResults_Sd_Recall		= new double[8][3];
		gridResults_Sd_F1			= new double[8][3];

		// mean and sd, may be not required
		mean_MAE 		= new double[3];	        
		mean_NMAE 		= new double[3];	        
		mean_RMSE 		= new double[3];

		mean_NMAEPerUser= new double[3];
		mean_RMSEPerUser= new double[3];
		mean_MAEPerUser = new double[3];

		mean_Coverage 	= new double[3];
		mean_ROC 		= new double[3];
		mean_BuildTime  = new double[5];
		mean_Precision	= new double[8][3];
		mean_Recall		= new double[8][3];
		mean_F1			= new double[8][3];	        

		sd_MAE 			= new double[3];	        
		sd_NMAE 		= new double[3];
		sd_RMSE 		= new double[3];

		sd_MAEPerUser	= new double[3];
		sd_NMAEPerUser 	= new double[3];
		sd_RMSEPerUser	= new double[3];


		sd_Coverage 	= new double[3];
		sd_ROC 			= new double[3];
		sd_BuildTime 	= new double[5];
		sd_Precision 	= new double[8][3];
		sd_Recall 		= new double[8][3];
		sd_F1		 	= new double[8][3];       

		myCentroids1   = new IntArrayList();
		myCentroids2   = new IntArrayList();
		myCentroids3   = new IntArrayList();
		myCentroids4   = new IntArrayList();
		myCentroids5   = new IntArrayList();



	}

	/************************************************************************************************/

	/**
	 *  It initialise an object and call the method for building the tree 
	 */

	//callno 1
	public void makeClusters(int callNo )     
	{
		//-----------------------
		// K-Means
		//-----------------------
		timer.start();	              
		nSpheres= singlePass.cluster(callNo, simVersion);       
		timer.stop();

		timer.getTime();
		System.out.println();
		System.out.println("Single pass took " + timer.getTime() + " s to build");    	
		timer.resetTimer();
	}

	/************************************************************************************************/
	/**
	 * Basic recommendation method for memory-based algorithms.
	 * 
	 * @param user
	 * @param movie
	 * @return the predicted rating, or -99 if it fails (mh error)
	 */

//	We call it for active user and a target movie
	public double recommendSphere(int activeUser, int targetMovie, int neighbours)    
	{
		double weightSum = 0, voteSum = 0;

//		LongArrayList movies = trainMMh.getMoviesSeenByUser(activeUser);     
//		 ---------------------------------------------------------------------------------------
//		 Start Recommending
//		 ---------------------------------------------------------------------------------------
		int activeClusterID= singlePass.getSpheresIDByUID(activeUser);
		
		OpenIntDoubleHashMap simMap = new OpenIntDoubleHashMap();				//sim b/w an active user and the clusters

		// Find sim b/w a user and the cluster he lies in        		
		double simWithMainCluster = singlePass.findSimWithOtherSphere(activeUser, activeClusterID );

		// Find sim b/w a user and all the other clusters
		for(int i=0;i<nSpheres; i++)
		{
			if(i!=activeClusterID)
			{
				double activeUserSim  = singlePass.findSimWithOtherSphere(activeUser, i );
				simMap.put(i,activeUserSim );      					
			} 

		} //end for

		// Put the mainCluster sim as well
		simMap.put(activeClusterID,simWithMainCluster );

		//sort the pairs (ascending order)
		IntArrayList keys		 = simMap.keys();
		DoubleArrayList vals 	 = simMap.values();        		
		simMap.pairsSortedByValue(keys, vals);        		
		int simSize 			  = simMap.size();
		int total =0;

		for (int i=simSize-1;i>=0;i--)
		{	
			//Get a cluster id
			int clusterId =keys.get(i);

			//Get currentCluster weight with the active user
			double clusterWeight =vals.get(i);

			//Get rating, average given by a cluster
			double clusterRating = singlePass.getRatingForAMovieInASphere(clusterId, targetMovie);
			double clusterAverage = singlePass.getAverageForAMovieInASphere(clusterId, targetMovie);

			if(clusterRating!=0)
			{
				//Prediction
				weightSum += Math.abs(clusterWeight);      		
				voteSum+= (clusterWeight*(clusterRating-clusterAverage)) ;

				if(total++ == neighbours) break;
			}

		}// end of for

		if (weightSum!=0)
			voteSum *= 1.0 / weightSum;        


		if (weightSum==0)				// If no weight, then it is not able to recommend????
		{ 
			//   System.out.println(" errror =" + answer);
			//   System.out.println(" vote sum =" +voteSum + ", weisghtSum ="+ weightSum);
			totalNan++;
			return 0;	       
		}

		double answer = trainMMh.getAverageRatingForUser(activeUser) + voteSum;             
		//------------------------
		// Send answer back
		//------------------------          

		if(answer<=0)
		{
			totalNegatives++;
			// return trainMMh.getAverageRatingForUser(activeUser);
			return 0;
		}

		else {
			totalRecSamples++;   
			return answer;
		}     
	}

	/************************************************************************************************/
	
	public static void main(String[] args)    
	{

		String path ="";
		/*Subset of SML
		String test  = "C:/Users/Musi/workspace/MusiRecommender/DataSets/SML_ML/Clustering/Sparsity/sml_TestSet20.dat";
		String base  = "C:/Users/Musi/workspace/MusiRecommender/DataSets/SML_ML/Clustering/Sparsity/sml_TrainSet80.dat";
		String base  = "C:/Users/Musi/workspace/MusiRecommender/DataSets/SML_ML/Clustering/Sparsity/sml_trainSetStoredAll_80_40.dat";

		SML
		path  = "C:/Users/Musi/workspace/MusiRecommender/DataSets/SML_ML/Clustering/FiveFoldData/80/";

		path  = "C:/Users/Musi/workspace/MusiRecommender/DataSets/ML_ML/SVD/80";
		 */
		//FT
		path  = "C:\\Users\\AsHi\\tempRecommender\\GitHubRecommender\\netflix\\netflix\\DataSets\\SML_ML\\FiveFoldData\\";


		//    String test = "C:/Users/Musi/workspace/MusiRecommender/DataSets/SML_ML/SVD/sml_TestSetStored.dat";
		//	  String base = "C:/Users/Musi/workspace/MusiRecommender/DataSets/SML_ML/SVD/sml_TrainSetStored.dat";

		//NF	    
		//  path= "/home/mag5v07/workspace/MusiRecommender/DataSets/NF/";

		//create class object
		SimpleKMeanAnytimeRecNFSimpleAll rec = new SimpleKMeanAnytimeRecNFSimpleAll();
		
		//Compute the resuts
		rec.computeResults(path);
	}

	/************************************************************************************************/

	/**
	 * Compute results over five fold and write them into a file
	 * We are curently using Log and Power version of Clustering. 
	 */

	public void computeResults(String path)
	{   
		myPath = path;

	//	optimal clusters, (1) sml =150, (2) ft1= 100, ft5 = 140

	//	myTotalFolds = 5;   
		openFile();
	// Build the tree based on training set only
		simVersion = 2;

		System.out.println("==========================================================================");
		System.out.println(" Single Pass Clustering ");
		System.out.println("==========================================================================");

//		fold<=myTotalFolds.......when to run for all folds......
		for(int fold=1 ;fold<=1;fold++)
		{ 	
			currentFold = fold;
			
			String  trainFile  = path  +  "sml_trainSetStoredFold" + (fold)+ ".dat";
			String  testFile	= path  +  "sml_testSetStoredFold" + (fold)+ ".dat";

			String  mainFile	= trainFile;

			allHelper = new MemHelper(mainFile);
			trainMMh  = new MemHelper(trainFile);
			testMMh 	= new MemHelper(testFile);	  


//			User- and item- based CF setting
			myUserBasedFilter = new FilterAndWeight(trainMMh,1); 		       
			myItemRec = new ItemItemRecommender(true, 5);

//			Make the objects and keep them fixed throughout the program
			for (int v=5;v<=5;v++)
			{	  
				//	object of single pass class
				singlePass = new SinglePass(trainMMh);	

				System.out.println("done reading objects");				   	  
				System.out.println("=====================");
				System.out.println(" Fold="+ fold);	 	
				System.out.println("=====================");

				//Build spheres ...myFlg is call number....
				makeClusters (myFlg);										  			   
						
				// We need to learn the no. of neighbours for the final thesis
				for(int neighbours = MIN_NEIGHBOURS; neighbours < MAX_NEAREST_NEIGHBOURS; neighbours+=NEIGHBOURS_INCREMENT) {
					timer.start();
					testWithMemHelper(testMMh,neighbours);
					timer.stop();
				}
				

			}
		}
	}

	/***************************************************************************************************/
	
	/**
	 * Using RMSE as measurement, this will compare a test set
	 * (in MemHelper form) to the results gotten from the recommender
	 *  
	 * @param testmh the MemHelper with test data in it   //check this what it meant........................Test data?///
	 * @return the rmse in comparison to testmh 
	 */
	public void testWithMemHelper(MemHelper testmh, int neighbours)     
	{

		RMSECalculator rmse = new  RMSECalculator();

		IntArrayList users;
		LongArrayList movies;
		double mov, pred,actual, uAvg;
		int uid, mid;    		       	
		int totalUsers						= 0;
		IntArrayList userThereInScenario	= new IntArrayList();

//		For each user, make recommendations
		users = testmh.getListOfUsers();
		totalUsers= users.size();

//		System.out.println(" List of user in RSME ......"+users);

		double uidToPredictions[][] = new double[totalUsers][101]; // 1-49=predictions; 50-99=actual; (Same order); 100=user average

		//________________________________________

		for (int i = 0; i < totalUsers; i++)        
		{

			uid = users.getQuick(i);    
			userThereInScenario.add(uid);
			movies = testmh.getMoviesSeenByUser(uid);

			for (int j = 0; j < movies.size(); j++)             
			{	      		            	
				mid = MemHelper.parseUserOrMovie(movies.getQuick(j));

				// double rrr = recommend(uid, mid, blank);                
				// double rrr = recommend(uid, mid, neighbours);

				double rrr = recommendSphere(uid, mid, neighbours);
				//  System.out.println(rrr);

				double myRating=0.0;

				//if (rrr!=0.0)                 
				{

					myRating = testmh.getRating(uid, mid);			 		// get actual ratings?

					//System.out.println(rrr+", "+ myRating);

					if (myRating==-99 )                           
						System.out.println(" rating error, uid, mid, rating" + uid + "," + mid + ","+ myRating);

					if(rrr>5 || rrr<=0)
					{
						/*                   		System.out.println("Prediction ="+ rrr + ", Original="+ myRating+ ", mid="+mid 
	                            		+", NewMid="+ myMoviesMap.get(mid)+ ", uid="+uid
	                            		+"No users who rated movie="+ mh.getNumberOfUsersWhoSawMovie(mid) + 
										", User saw movies="+mh.getNumberOfMoviesSeen(uid));*/
					}



					//-------------
					// Add ROC
					//-------------
					if(rrr!=0)
						rmse.ROC4(myRating, rrr, myClasses, trainMMh.getAverageRatingForUser(uid));		
					//rmse.ROC4(myRating, rrr, myClasses, TopNThreshold);

					//-------------
					//Add Error
					//-------------

					if(rrr!=0)
					{
						rmse.add(myRating,rrr);                            	
						midToPredictions.put(mid, rrr);                            	                                
					}		


					//-------------
					//Add Coverage
					//-------------

					rmse.addCoverage(rrr);                                 
				}         

			} //end of movies for

			//--------------------------------------------------------
			//A user has ended, now, add ROC and reset
			rmse.addROCForOneUser();
			rmse.resetROCForEachUser();
			rmse.addMAEOfEachUserInFinalMAE();
			rmse.resetMAEForEachUser();
			
			//sort the pairs (ascending order)
			IntArrayList keys = midToPredictions.keys();
			DoubleArrayList vals = midToPredictions.values();        		
			midToPredictions.pairsSortedByValue(keys, vals);

			int movSize = midToPredictions.size();
			if(movSize>50)
				movSize = 50;      	

			for(int x=0;x<movSize;x++)
			{
				mov = keys.getQuick(x);
				pred = vals.getQuick(x);
				actual = testmh.getRating(uid,(int) mov);	
				uidToPredictions[i][x] = pred;
				uidToPredictions[i][50+x] = actual;
			}//end for

			uidToPredictions[i][100] = trainMMh.getAverageRatingForUser(uid);
			midToPredictions.clear();

		} //end of user for	   

		MAE		 	= rmse.mae(); 
		SDInMAE		= rmse.getMeanErrorOfMAE();
		SDInROC 	= rmse.getMeanSDErrorOfROC();
		Roc 		= rmse.getSensitivity();
		MAEPerUser 	= rmse.maeFinal();
		RMSE 		= rmse.rmse();
		RMSEPerUser	= rmse.rmseFinal();
		coverage	= rmse.getItemCoverage();

//		kMeanEigen_Nmae 	= rmse.nmae_Eigen(1,5);
//		kMeanCluster_Nmae 	= rmse.nmae_ClusterKNNFinal(1, 5);
		
		//-------------------------------------------------
		//Calculate top-N results (e.g. F1, Precision, Recall)  		            

		for(int i=0;i<8;i++)	//N from 5 to 30
		{
			for(int j=0;j<totalUsers;j++)//All users
			{
				int tempUid = users.getQuick(j);

				//check if this user was there in this scenario
				if(userThereInScenario.contains(tempUid))
				{
					//get user avg
					uAvg =  uidToPredictions [j][100];	

					for(int k=0;k<((i+1)*5);k++)	//for topN predictions
					{
						//get prediction and actual vals
						pred =  uidToPredictions [j][k];
						actual =  uidToPredictions [j][50+k];

						//add to topN
						rmse.addTopN(actual, pred, myClasses, uAvg);
						// rmse.addTopN(actual, pred, myClasses, TopNThreshold);
					}

					//after each user, first add TopN, and then reset
					rmse.AddTopNPrecisionRecallAndF1ForOneUser();
					rmse.resetTopNForOneUser();   		            		
				}
			} //end for
//		--------------------------------------------------------------------------------------------------------------
			//Now we finish finding Top-N for a particular value of N
			//Store it 
			
			precision[i]=rmse.getTopNPrecision();
			recall[i]=rmse.getTopNRecall();
			F1[i]=rmse.getTopNF1(); 

			//Get variance 
			SDInTopN_Precision[i] = rmse.getMeanSDErrorOfTopN(1); 
			SDInTopN_Recall[i] = rmse.getMeanSDErrorOfTopN(2);
			SDInTopN_F1 [i]= rmse.getMeanSDErrorOfTopN(2);

			//Reset all topN values    		            	
			rmse.resetTopNForOneUser();
			rmse.resetFinalTopN();

		} //end of for   		        	

		
		//Reset final values
		rmse.resetValues();   
		rmse.resetFinalROC();
		rmse.resetFinalMAE();


//		--------------------------------------------------------------------------------------
//		Printing of results
		System.out.println(" Spheres = " + nSpheres ); 	
		System.out.print("Coverage="+coverage);
		System.out.print(",");
		System.out.print("MAE="+MAE);
		System.out.print(",");
		System.out.print("ROC="+Roc);
		System.out.print(",");  	   
		System.out.print("\n F1="+F1[3]);
		System.out.print(",");
		System.out.print("PRECISION="+precision[3]);
		System.out.print(",");
		System.out.print("RECALL="+recall[3]);
		System.out.print(",");   		
		System.out.print("\n");

		//if(version ==4)  System.out.println(" alpha =" + (alpha -0.1) + ", beta ="+ (beta+0.1) );
		System.out.println("answered  = "+ totalRecSamples + 
				", nan= "+ totalNan+ ", -ve= "+ totalNegatives);
		System.out.println("------------");

	}//end of function
	
	
	/************************************************************************************************/

	/**
	 * @param int, no. of clusters
	 * @param double, simThr
	 */
	public void WriteResultsInFiles(int k, int sThr, int fold, int myGSU)
	{    	
		int START = 0;

		try {			   

				for(int gsu=START;gsu<=2;gsu++)
				{	

					System.out.println("Came to write ");

					//start actual writing (Means)
					writeData1.append(""+kClusters);
					writeData1.append(",");
					writeData1.append(""+gridResults_Mean_GSU[gsu]);
					writeData1.append(",");
					writeData1.append(""+gridResults_Mean_GSUSamples[gsu]);
					writeData1.append(",");

					writeData1.append(""+simThreshold);
					writeData1.append(",");
					writeData1.append(""+powerUsersThreshold);
					writeData1.append(",");			   	
					writeData1.append(""+numberOfneighbours);
					writeData1.append(",");			   	
					writeData1.append(""+ totalIterations);
					writeData1.append(",");		   	
					writeData1.append(""+gsu);
					writeData1.append(",");

					writeData1.append(""+gridResults_Mean_Coverage[gsu]);
					writeData1.append(",");
					writeData1.append(""+gridResults_Mean_MAE[gsu]);
					writeData1.append(",");
					writeData1.append(""+gridResults_Mean_MAEPerUser[gsu]);
					writeData1.append(",");
					writeData1.append(""+gridResults_Mean_RMSE[gsu]);
					writeData1.append(",");
					writeData1.append(""+gridResults_Mean_RMSEPerUser[gsu]);
					writeData1.append(",");
					writeData1.append(""+gridResults_Mean_ROC[gsu]);
					writeData1.append(",");							   	   

					writeData1.append(""+gridResults_Mean_F1[3][gsu]);
					writeData1.append(",");
					writeData1.append(""+gridResults_Mean_Precision[3][gsu]);
					writeData1.append(",");
					writeData1.append(""+gridResults_Mean_Recall[3][gsu]);
					writeData1.append(",");   		


					writeData1.append(""+gridResults_Mean_F1[2][gsu]);
					writeData1.append(",");
					writeData1.append(""+gridResults_Mean_Precision[2][gsu]);
					writeData1.append(",");
					writeData1.append(""+gridResults_Mean_Recall[2][gsu]);
					writeData1.append(",");   					   	   

					writeData1.append(""+gridResults_Mean_F1[1][gsu]);
					writeData1.append(",");
					writeData1.append(""+gridResults_Mean_Precision[1][gsu]);
					writeData1.append(",");
					writeData1.append(""+gridResults_Mean_Recall[1][gsu]);
					writeData1.append(",");   		
					writeData1.append("\n");




					//start actual writing (SD)
					writeData2.append(""+kClusters);
					writeData2.append(",");
					writeData2.append(""+gridResults_Mean_GSU[gsu]);
					writeData2.append(",");
					writeData2.append(""+gridResults_Mean_GSUSamples[gsu]);
					writeData2.append(",");

					writeData2.append(""+simThreshold);
					writeData2.append(",");
					writeData2.append(""+powerUsersThreshold);
					writeData2.append(",");
					writeData2.append(""+numberOfneighbours);
					writeData2.append(",");
					writeData2.append(""+ totalIterations);
					writeData2.append(",");	
					writeData2.append(""+gsu);
					writeData2.append(",");
					writeData2.append(""+gridResults_Sd_Coverage[gsu]);
					writeData2.append(",");
					writeData2.append(""+gridResults_Sd_MAE[gsu]);
					writeData2.append(",");
					writeData2.append(""+gridResults_Sd_MAEPerUser[gsu]);
					writeData2.append(",");
					writeData2.append(""+gridResults_Sd_RMSE[gsu]);
					writeData2.append(",");
					writeData2.append(""+gridResults_Sd_RMSEPerUser[gsu]);
					writeData2.append(",");
					writeData2.append(""+gridResults_Sd_ROC[gsu]);
					writeData2.append(",");							   	   

					writeData2.append(""+gridResults_Sd_F1[3][gsu]);
					writeData2.append(",");
					writeData2.append(""+gridResults_Sd_Precision[3][gsu]);
					writeData2.append(",");
					writeData2.append(""+gridResults_Sd_Recall[3][gsu]);
					writeData2.append(",");   		


					writeData2.append(""+gridResults_Sd_F1[2][gsu]);
					writeData2.append(",");
					writeData2.append(""+gridResults_Sd_Precision[2][gsu]);
					writeData2.append(",");
					writeData2.append(""+gridResults_Sd_Recall[2][gsu]);
					writeData2.append(",");   		


					writeData2.append(""+gridResults_Sd_F1[1][gsu]);
					writeData2.append(",");
					writeData2.append(""+gridResults_Sd_Precision[1][gsu]);
					writeData2.append(",");
					writeData2.append(""+gridResults_Sd_Recall[1][gsu]);

					writeData2.append(",");   		
					writeData2.append("\n");

					//--------------------------------------------------------------------------------------------------- 	    	
					System.out.println(" Cluster = " + k+ ", gsu="+ gsu +", sim Thr = " +sThr +", powerThr"+ powerUsersThreshold ); 	
					System.out.print("Coverage="+gridResults_Mean_Coverage[gsu]);
					System.out.print(",");
					System.out.print("MAE="+gridResults_Mean_MAE[gsu]);
					System.out.print(",");
					System.out.print("ROC="+gridResults_Mean_ROC[gsu]);
					System.out.print(",");  	   
					System.out.print("\n F1="+gridResults_Mean_F1[3][gsu]);
					System.out.print(",");
					System.out.print("PRECISION="+gridResults_Mean_Precision[3][gsu]);
					System.out.print(",");
					System.out.print("RECALL="+gridResults_Mean_Recall[3][gsu]);
					System.out.print(",");   		
					System.out.print("\n");

					//if(version ==4)  System.out.println(" alpha =" + (alpha -0.1) + ", beta ="+ (beta+0.1) );
					System.out.println("answered  = "+ totalRecSamples + 
							", nan= "+ totalNan+ ", -ve= "+ totalNegatives);
					System.out.println("gray sheep users="+ graySheepUsers+", gray sheep predictions="+ graySheepSamples);
					System.out.println("--------------------------------------------------------------------------------------------------- ");

				} //end for

		}//end try


		catch (Exception E)
		{
			System.out.println("error writing the file pointer of rec");
			E.printStackTrace();
			//     	  System.exit(1);
		}

	}//end method


	/********************************************************************************************************************************/

	//-----------------------------


	public void openFile()    
	{

		try 
		{
			writeData1 = new BufferedWriter(new FileWriter(myPath + "new.csv", true));   			
			writeData2 = new BufferedWriter(new FileWriter(myPath + "new.csv", true));	
			System.out.println("Rec File Created at"+ "new.csv");												  
		}

		catch (Exception E)
		{
			System.out.println("error opening the file pointer of rec");
			System.exit(1);
		}

	}

	//----------------------------

	public void closeFile()    
	{
		try 
		{
			writeData1.close();
			writeData2.close();
			System.out.println("Files closed");
		}

		catch (Exception E)
		{
			System.out.println("error closing the roc file pointer");
		}
	}

	//---------------------------------------


}//end class