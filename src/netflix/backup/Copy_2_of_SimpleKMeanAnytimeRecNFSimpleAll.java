package backup;

import java.io.BufferedWriter;


import java.io.FileWriter;
import java.util.*;


import netflix.algorithms.memorybased.memreader.FilterAndWeight;
import netflix.memreader.*;
import netflix.recommender.ItemItemRecommender;
import netflix.rmse.RMSECalculator;
import netflix.utilities.*;
import cern.colt.list.*;
import cern.colt.map.*;

/************************************************************************************************/
public class Copy_2_of_SimpleKMeanAnytimeRecNFSimpleAll
{
    
	private SinglePass						singlePass;
	
	private int								myClasses;
	private int								myTotalFolds;
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
    private int			KMeansOrKMeansPlus; 
    private int			simVersion;
    
    //Related to finding the gray sheep user's predictions
    private int			graySheepUsers;							// total gray sheep users
    private int			graySheepSamples;						// total gray sheep predictions
    private int			isGraySheepCluserOrAllClusters;			// wanna find predictions for g.s.u.'s custers or all clusters 
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
    
    
/************************************************************************************************/
    
    public Copy_2_of_SimpleKMeanAnytimeRecNFSimpleAll()    
    {
       
    	 totalRecSamples 	= 0;
    	 myClasses			= 5;
    	 simVersion			= 1;  //1=PCCwithDefault, 2=PCCwithoutDefault
    	 						  //3=VSWithDefault,  4=VSWithDefault
    	 						  //5=PCC, 			  6=VS
    	 
    	 graySheepUsers   	 = 0;
    	 graySheepSamples 	 = 0;
    	 KMeansOrKMeansPlus  = 0;
    	 
    	 timer 				 = new Timer227();
    	 MEANORSD			 = new MeanOrSD();
         


         numberOfneighbours  = 0;
	        totalIterations		= 0;
	         
         	MAE 				= 0;
	    	MAEPerUser			= 0;
	    	RMSE 				= 0;
			RMSEPerUser 		= 0;
	    	kMeanEigen_Nmae		= 0;
	    	kMeanCluster_Nmae	= 0;
	    	Roc 				= 0;
	    	coverage			= 0;
	    	pValue				= 0;
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
	    	 array_MAE  	 	=   new double[3][5];
	    	 array_MAEPerUser	=   new double[3][5]; 
	    	 array_NMAE		 	=   new double[3][5];
	    	 array_NMAEPerUser	=   new double[3][5];
	    	 array_RMSE 	 	=   new double[3][5];
	    	 array_RMSEPerUser 	=   new double[3][5];
	         array_Coverage  	=   new double[3][5];
	         array_ROC 		 	=   new double[3][5];
	         array_BuildTime 	=   new double[3][5];
	         array_GSU  	 	=   new double[3][5];
	         array_GSUSamples 	=   new double[3][5];
	         
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
	         gridResults_Mean_GSU			=   new double[3];
	         gridResults_Mean_GSUSamples	=   new double[3];
	         
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
	         gridResults_sd_GSU			=   new double[3];
	         gridResults_sd_GSUSamples	=   new double[3];
	         
	         
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
    public void callKTree(int callNo )     
    {
    	//5,3,3,3,3; KMeans was like 1.13 and remaining 1.08 RMSE
    	// 5,5,5,5,5; the diff is like 1.11 and 1.06
        //-----------------------
    	// K-Means
    	//-----------------------
    	
    	
    	if(KMeansOrKMeansPlus==1)
    	{
	    	timer.start();	              
	        nSpheres= singlePass.cluster(callNo, simVersion);       
	        timer.stop();
	        
	        timer.getTime();
	        System.out.println();
	        System.out.println("Single pass took " + timer.getTime() + " s to build");    	
	      //  System.out.println(nSpheres+" no of spheres passed to main function......");
	        timer.resetTimer();
	        //System.gc();
    	}

    }
	     
/************************************************************************************************/
    
    /**
     * Correlation weighting between two users
     * 
     * @param  mh the database to use
     * @param  activeUser the active user
     * @param  targetUser the target user
     * @return their correlation
     */
    
    private double correlation(int activeUser, int targetUser)    
    {
    	int amplifyingFactor = 1;			//give more weight if users have more than 50 movies in common
    	
    	double topSum, bottomSumActive, bottomSumTarget, rating1, rating2;
        topSum = bottomSumActive = bottomSumTarget = 0;
        double functionResult=0.0;
               
        double activeAvg = trainMMh.getAverageRatingForUser(activeUser);
        double targetAvg = trainMMh.getAverageRatingForUser(targetUser);
    
        ArrayList<Pair> ratings = trainMMh.innerJoinOnMoviesOrRating(activeUser, targetUser, true);
		
        // Do the summations
        for(Pair pair : ratings)         
        {
            rating1 = (double)MemHelper.parseRating(pair.a) - activeAvg;
            rating2 = (double)MemHelper.parseRating(pair.b) - targetAvg;
			
            topSum += rating1 * rating2;
            bottomSumActive += Math.pow(rating1, 2);
            bottomSumTarget += Math.pow(rating2, 2);
        }
		
        double n = ratings.size() - 1;
        
        //So we get results even if they match on only one item
        //(Better than nothing, right?)
        if(n == 0)
            n++;
        
       // This handles an emergency case of dividing by zero
       if (bottomSumActive != 0 && bottomSumTarget != 0)
       { 
       	
       	functionResult = (1 * topSum) / Math.sqrt(bottomSumActive * bottomSumTarget);  //why multiply by n?
       	return  functionResult * (n/amplifyingFactor); //amplified send 
       	
       }
       
       else
        //   return 1;			// why return 1:?????
       	return 0;			// So in prediction, it will send average back 
       
    }

 /************************************************************************************************/
    
    /**
     * Basic recommendation method for memory-based algorithms.
     * 
     * @param user
     * @param movie
     * @return the predicted rating, or -99 if it fails (mh error)
     */
 
 //We call it for active user and a target movie
    public double recommend(int activeUser, int targetMovie, int neighbours)    
    {
        double currWeight, weightSum = 0, voteSum = 0;
        int uid; 
        double  neighRating=0;
      
        //done by me...... else it was null......
        
        
        IntArrayList simpleKUsers = new IntArrayList(neighbours); 
        int limit = 50;
        
     // variable for priors, and sim * priors
	     double priors[] = new double[5];
	     double priorsMultipliedBySim[] = new double[5];
        
	     //Active User's class prior
	     double activeUserPriors[] = new double[5];
	     LongArrayList movies = trainMMh.getMoviesSeenByUser(activeUser);         
         int moviesSize = movies.size();
         for (int i=0;i<moviesSize;i++)
         {                	
         	  int mid = MemHelper.parseUserOrMovie(movies.getQuick(i));
         	  double rating = trainMMh.getRating(activeUser, mid);
         	  int index = (int) rating;
         	 // activeUserPriors[index-1]++;
          	         	
         }

                  
        //IntArrayList mixedUsers =  mixedTree.getClusterByUID(activeUser); 	  	  //mixed tree and K users
	   
        //---------------------------------------------------------------------------------------
        // Start Recommending
        //---------------------------------------------------------------------------------------
      
      {
        
     //   IntArrayList treeUsers 		= tree.getClusterByUID(activeUser);		 	//simple tree users
     //   int userClusterIndex      	= tree.getClusterIDByUID(activeUser);
          LongArrayList tempUsers 		= trainMMh.getUsersWhoSawMovie(targetMovie);
          IntArrayList userWhichSawThisMovie = new IntArrayList();
          
          for(int i = 0; i < tempUsers.size(); i++)
          {
        	  uid = MemHelper.parseUserOrMovie(tempUsers.getQuick(i));
        	  userWhichSawThisMovie.add(uid);
          }
                  
          
          //----------------------------------------------
          // FILTER (where should we filter)?
          // Filter movies....i.e. less than 1 rating
          //---------------------------------------------- 
          
         // System.out.println ("uid, mid " + activeUser +","+ targetMovie);
          double recommendation   = 0.0;  
	  
          //----------------------------------------------
          // Go through all the users in that cluster
          //----------------------------------------------          
          
          OpenIntDoubleHashMap uidToWeight = new  OpenIntDoubleHashMap();
          IntArrayList myUsers      	   = new IntArrayList();
          DoubleArrayList myWeights 	   = new DoubleArrayList();
          int totalNeighbourFound   	   = 0;
         
          
	    for(int i = 0; i < simpleKUsers.size(); i++) //go through all the users in the cluster - (created by Kmean)
	     {
    		 uid = simpleKUsers.getQuick(i);    	 

          if (userWhichSawThisMovie.contains(uid)) 	//so this user has seen movie
           {       
        	  neighRating = trainMMh.getRating(uid, targetMovie);	//get rating of ratings of each user for the target movie
             
            //If the user rated the target movie and the target
            //user is not the same as the active user. 
	            if(neighRating != -99 && uid != activeUser)
	             {              
	                currWeight = correlation(activeUser, uid);
	                uidToWeight.put(uid, currWeight);
	                weightSum += Math.abs(currWeight);
	                //voteSum += currWeight * (rating - helper.getAverageRatingForUser(uid));	
	                
	              } //end of if rating
             } //end of if user saw movie
           } // End of all users

	       myUsers 		= uidToWeight.keys();
	       myWeights 	= uidToWeight.values();
	       uidToWeight.pairsSortedByValue(myUsers, myWeights);
	       
	       //---------------------------
	       // get top weights and use
	       //---------------------------
	       	       
	       for (int i = totalNeighbourFound-1, myTotal=0; i >=0; i--, myTotal++)       
	       {    	   
	       		if(myTotal == limit) break;       	
	       		uid = myUsers.get(i);       	
	       
	   
     	   	   // Taste approach
            	currWeight= (myWeights.get(i)+1);
            	weightSum += Math.abs(currWeight+1);
       	    	neighRating = trainMMh.getRating(uid, targetMovie);        
         	    voteSum+= (currWeight* (neighRating  - trainMMh.getAverageRatingForUser(uid))) ;
       
       		
         	    //Simple, but do not take -ve into accounts
        		  currWeight= (myWeights.get(i));      	 
		       	 // if (currWeight>0)
		       		{	
		       			weightSum += Math.abs(currWeight);      		
		           		neighRating = trainMMh.getRating(uid, targetMovie);        
		           		voteSum+= ( currWeight* (neighRating  - trainMMh.getAverageRatingForUser(uid))) ;
		       		} //end of weight should be positive
       
		       		// Take all weights into account		       		
		       		currWeight= (myWeights.get(i));	       			
		       		weightSum += Math.abs(currWeight);      		
		           	neighRating = trainMMh.getRating(uid, targetMovie);        
		           	voteSum+= ( currWeight* (neighRating  - trainMMh.getAverageRatingForUser(uid))) ;
		      }
	       
	       if (weightSum!=0)
	    	   voteSum *= 1.0 / weightSum;        
        
	       //-----------------------------
	       // Coverage?
	       //-----------------------------
	       
	       if (weightSum==0)				// If no weight, then it is not able to recommend????
	       { 
	    	   //   System.out.println(" errror =" + answer);
	           //   System.out.println(" vote sum =" +voteSum + ", weisghtSum ="+ weightSum);
	    	   
	         	 totalNan++;
	         	 return 0;	       
	       }
	       	       
	       double answer = trainMMh.getAverageRatingForUser(activeUser) + voteSum;             
       // System.gc(); // It slows down the system to a great extent

        //------------------------
        // Send answer back
        //------------------------          
      
         if(answer<=0)
         {

         	 totalNegatives++;
         	  //return trainMMh.getAverageRatingForUser(activeUser);
         	 return 0;
         }
         
         else {
        	 totalRecSamples++;   
        	 return answer;
         }
         
      }//end if
                
	   
	   //---------------------------------------------
	   // Simple using CF--User-based or Item-based
	   //---------------------------------------------	   
	  
	   
	  /* if (KMeansOrKMeansPlus==6)
	   {	   
		   //first go in these programs and return "0" or at-least the averages if they fail to predict
		   double rat = myUserBasedFilter.recommendS(activeUser, targetMovie, 30, 1);
		   return rat;
		   
	   }	   
	   
	   else if (KMeansOrKMeansPlus==7)
	   {	   
		   double rat = myItemRec.recommend(trainMMh, activeUser, targetMovie, 15, 4);	   
		   return rat;
		   
	   }
	   */
	   
/*	   //---------------------------------------------
	   // Averages
	   //---------------------------------------------	   
	
	   else if (KMeansOrKMeansPlus==12)
	   {	   
					double		rat = (trainMMh.getAverageRatingForMovie(targetMovie) + 
    									 trainMMh.getAverageRatingForUser(activeUser))/2.0;
    			
					return rat;
	   }
	   
*/	   
//	   return 0;
        
    }

/************************************************************************************************/
    
    public double recommendSphere(int activeUser, int targetMovie, int neighbours)    
    {
        double weightSum = 0, voteSum = 0;
         
        int limit =50;
        
        IntArrayList simpleKUsers = null; 
        LongArrayList movies = trainMMh.getMoviesSeenByUser(activeUser);     
	     //System.out.println(movies);
         int moviesSize = movies.size();
         for (int i=0;i<moviesSize;i++)
         {                	
         	  int mid = MemHelper.parseUserOrMovie(movies.getQuick(i));
         	  double rating = trainMMh.getRating(activeUser, mid);
//         	  int index = (int) rating;
//         	  activeUserPriors[index-1]++;
          	         	
         }

        
        	 simpleKUsers = singlePass.getSphereByUID(activeUser);                  //spheres containing the user........ in my case it will be list of spheres.........
        	
    	//int activeClusterID = simpleKTree.getSphereIDByUID(activeUser);
    		      
        //IntArrayList mixedUsers =  mixedTree.getClusterByUID(activeUser); 	  	  //mixed tree and K users
	   
        //---------------------------------------------------------------------------------------
        // Start Recommending
        //---------------------------------------------------------------------------------------
       //  if (KMeansOrKMeansPlus == 1)
         
         /// first method.............................................
         {
        	 int activeClusterID= singlePass.getSpheresIDByUID(activeUser);
//        	 System.out.println(res+ "  **********************************************************************************");
        	// int activeClusterID = singlePass.getSphereIDByUID(activeUser);
       		
        	// System.out.println(activeClusterID);
       	
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
        		LongArrayList tempUsers   = trainMMh.getUsersWhoSawMovie(targetMovie);
        		LongArrayList allUsers    = new LongArrayList();
        		
        		int total =0;
        		
        		for (int i=simSize-1;i>=0;i--)
        		{	
        			//Get a cluster id
        			int clusterId =keys.get(i);
        			
        			//Get currentCluster weight with the active user
        			double clusterWeight =vals.get(i);
        		//	System.out.println(" weight ("+ (clusterId)+") with i= " + i + ",-->"+ clusterWeight);
        			
 					//Get rating, average given by a cluster
 					double clusterRating = singlePass.getRatingForAMovieInASphere(clusterId, targetMovie);
 					double clusterAverage = singlePass.getAverageForAMovieInASphere(clusterId, targetMovie);
 					
// 					System.out.println(" rating ="+clusterRating);
// 		 	        System.out.println(" avg ="+ clusterAverage);
// 		 	    	   
 					if(clusterRating!=0)
 					{
 						//Prediction
 			       		weightSum += Math.abs(clusterWeight);      		
 			           	voteSum+= (clusterWeight*(clusterRating-clusterAverage)) ;
 			           	
 			           	if(total++ == 70) break;
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
 		        // System.gc(); // It slows down the system to a great extent

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
    	
    ///////	second way...................
     /* {
        
     //   IntArrayList treeUsers 		= tree.getClusterByUID(activeUser);		 	//simple tree users
     //   int userClusterIndex      	= tree.getClusterIDByUID(activeUser);
          LongArrayList tempUsers 		= trainMMh.getUsersWhoSawMovie(targetMovie);
          
//          System.out.println("users who saw same movie...." + tempUsers);
          
          IntArrayList userWhichSawThisMovie = new IntArrayList();
          
          int uid;
		for(int i = 0; i < tempUsers.size(); i++)
          {
        	  uid = MemHelper.parseUserOrMovie(tempUsers.getQuick(i));
        	  userWhichSawThisMovie.add(uid);
          }
                  
          
          //----------------------------------------------
          // FILTER (where should we filter)?
          // Filter movies....i.e. less than 1 rating
          //---------------------------------------------- 
          
         // System.out.println ("uid, mid " + activeUser +","+ targetMovie);
          double recommendation   = 0.0;  
	  
          //----------------------------------------------
          // Go through all the users in that cluster
          //----------------------------------------------          
          
          OpenIntDoubleHashMap uidToWeight = new  OpenIntDoubleHashMap();
          IntArrayList myUsers      	   = new IntArrayList();
          DoubleArrayList myWeights 	   = new DoubleArrayList();
          int totalNeighbourFound   	   = 0;
         
          
	    double currWeight;
		double neighRating;
		for(int i = 0; i < simpleKUsers.size(); i++) //go through all the users in the cluster - (created by Kmean)
	     {
    		 uid = simpleKUsers.getQuick(i);    	 
    		 //System.out.println("uid of user in cluster....."+ uid);
          if (userWhichSawThisMovie.contains(uid)) 	//so this user has seen movie
           {       
        	  neighRating = trainMMh.getRating(uid, targetMovie);	//get rating of ratings of each user for the target movie
             
            //If the user rated the target movie and the target
            //user is not the same as the active user. 
	            if(neighRating != -99 && uid != activeUser)
	             {              
	                currWeight = correlation(activeUser, uid);
	                uidToWeight.put(uid, currWeight);
	                weightSum += Math.abs(currWeight);
	                //voteSum += currWeight * (rating - helper.getAverageRatingForUser(uid));	
	                
	              } //end of if rating
             } //end of if user saw movie
           } // End of all users

	       myUsers 		= uidToWeight.keys();
	       myWeights 	= uidToWeight.values();
	       uidToWeight.pairsSortedByValue(myUsers, myWeights);
	       
	       //---------------------------
	       // get top weights and use
	       //---------------------------
	       	       
	       for (int i = totalNeighbourFound-1, myTotal=0;  i >=0  ; i--, myTotal++)       
	       {    	   
	       		
				if(myTotal == limit ) break;       	
	       		uid = myUsers.get(i);       	
	       
	   
     	   	   // Taste approach
            	currWeight= (myWeights.get(i)+1);
            	weightSum += Math.abs(currWeight+1);
       	    	neighRating = trainMMh.getRating(uid, targetMovie);        
         	    voteSum+= (currWeight* (neighRating  - trainMMh.getAverageRatingForUser(uid))) ;
       
       		
         	    //Simple, but do not take -ve into accounts
        		  currWeight= (myWeights.get(i));      	 
		       	 // if (currWeight>0)
		       		{	
		       			weightSum += Math.abs(currWeight);      		
		           		neighRating = trainMMh.getRating(uid, targetMovie);        
		           		voteSum+= ( currWeight* (neighRating  - trainMMh.getAverageRatingForUser(uid))) ;
		       		} //end of weight should be positive
       
		       		// Take all weights into account		       		
		       		currWeight= (myWeights.get(i));	       			
		       		weightSum += Math.abs(currWeight);      		
		           	neighRating = trainMMh.getRating(uid, targetMovie);        
		           	voteSum+= ( currWeight* (neighRating  - trainMMh.getAverageRatingForUser(uid))) ;
		      }
	       
	       if (weightSum!=0)
	    	   voteSum *= 1.0 / weightSum;        
        
	       //-----------------------------
	       // Coverage?
	       //-----------------------------
	       
	       if (weightSum==0)				// If no weight, then it is not able to recommend????
	       { 
	    	   //   System.out.println(" errror =" + answer);
	           //   System.out.println(" vote sum =" +voteSum + ", weisghtSum ="+ weightSum);
	    	   
	         	 totalNan++;
	         	 return 0;	       
	       }
	       	       
	       double answer = trainMMh.getAverageRatingForUser(activeUser) + voteSum;    
	       
	    //   System.out.println(" recomendation.......for user "+activeUser + " and movie id "+ targetMovie +answer);
       // System.gc(); // It slows down the system to a great extent

        //------------------------
        // Send answer back
        //------------------------          
      
         if(answer<=0)
         {

         	 totalNegatives++;
         	  //return trainMMh.getAverageRatingForUser(activeUser);
         	 return 0;
         }
         
         else {
        	 totalRecSamples++;   
        	 return answer;
         }
         
      }//end if
*/                
	   
	   //---------------------------------------------
	   // Simple using CF--User-based or Item-based
	   //---------------------------------------------	   
	  
	   
	  /* if (KMeansOrKMeansPlus==6)
	   {	   
		   //first go in these programs and return "0" or at-least the averages if they fail to predict
		   double rat = myUserBasedFilter.recommendS(activeUser, targetMovie, 30, 1);
		   return rat;
		   
	   }	   
	   
	   else if (KMeansOrKMeansPlus==7)
	   {	   
		   double rat = myItemRec.recommend(trainMMh, activeUser, targetMovie, 15, 4);	   
		   return rat;
		   
	   }
	   */
	   
/*	   //---------------------------------------------
	   // Averages
	   //---------------------------------------------	   
	
	   else if (KMeansOrKMeansPlus==12)
	   {	   
					double		rat = (trainMMh.getAverageRatingForMovie(targetMovie) + 
    									 trainMMh.getAverageRatingForUser(activeUser))/2.0;
    			
					return rat;
	   }
	   
*/	   
//	   return 0;
        
    }

/************************************************************************************************/
    
    
  public static void main(String[] args)    
  {
    	
	      String path ="", mainFile="", base="", test="";
	      int    fold = 1;
	    
	   // Subset of SML
	/*	  String test  = "C:/Users/Musi/workspace/MusiRecommender/DataSets/SML_ML/Clustering/Sparsity/sml_TestSet20.dat";
	//	  String base  = "C:/Users/Musi/workspace/MusiRecommender/DataSets/SML_ML/Clustering/Sparsity/sml_TrainSet80.dat";
		  String base  = "C:/Users/Musi/workspace/MusiRecommender/DataSets/SML_ML/Clustering/Sparsity/sml_trainSetStoredAll_80_40.dat";
	*/	  
	     //SML
	     // path  = "C:/Users/Musi/workspace/MusiRecommender/DataSets/SML_ML/Clustering/FiveFoldData/80/";
	 
	     // path  = "C:/Users/Musi/workspace/MusiRecommender/DataSets/ML_ML/SVD/80";
	  
	      //FT
	      path  = "C:\\Users\\AsHi\\tempRecommender\\GitHubRecommender\\netflix\\netflix\\DataSets\\SML_ML\\FiveFoldData\\";

	      
	//    String test = "C:/Users/Musi/workspace/MusiRecommender/DataSets/SML_ML/SVD/sml_TestSetStored.dat";
	//	  String base = "C:/Users/Musi/workspace/MusiRecommender/DataSets/SML_ML/SVD/sml_TrainSetStored.dat";
		
		  //NF	    
		  //  path= "/home/mag5v07/workspace/MusiRecommender/DataSets/NF/";
		 
		    //create class object
		    Copy_2_of_SimpleKMeanAnytimeRecNFSimpleAll rec = new Copy_2_of_SimpleKMeanAnytimeRecNFSimpleAll();
		 	
		    //Build SVM reg model, will be called only once
		   // rec.buildSVMRegModel();
		    
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
	 

	    /*   
	    //_____________________________
        // For Svd writing let 6 clusters
        //_____________________________ 
	    
		rec.kClusters = 6;
    	rec.callKTree ();
	     */ 
	   
  
	   //optimal clusters, (1) sml =150, (2) ft1= 100, ft5 = 140
	   
   myTotalFolds = 5;   
   int START    = 2;  // 0=gsu, 1=remaining users, 2=all users
   openFile();
   
   
		 
	//Clusters  
   for (int k=nSpheres;k<=nSpheres;k+=100)	    
   {    
	  //Build the tree based on training set only
	 //  kClusters  = k;
	   simVersion = 2;
		  
	   System.out.println("==========================================================================");
	   System.out.println(" Single Pass Clustering ");
	   System.out.println("==========================================================================");
	 	  	   
	 	  
	 	//version (we'll call log power one)
		 		 
		 	  KMeansOrKMeansPlus = 1;		 	  
		 	
		 	 	 
		   
					 
					 //Find the optimal no. of neighbours	 
					 for(int noNeigh = 30; noNeigh<=30;noNeigh+=10)
					 {
					  	numberOfneighbours = noNeigh;
							
					  
					  //	 was in for loop
					   	
				    for(int fold=1 ;fold<=myTotalFolds;fold++)
				    { 	
				    	currentFold = fold;
				    	
				    	
				 
				    	//SML				    	
				   	 /*   String  trainFile = path +  "sml_trainSetStoredFold" + (fold)+ ".dat";
					    String  testFile  = path +  "sml_testSetStoredFold" + (fold)+ ".dat";
					  */
					   
			/*		   String  trainFile   = myPath  + "sml_trainingTrainSetStoredFold" +(fold) + ".dat";   	 
					   String  testFile    = myPath + "sml_trainingValSetStoredFold" +(fold) + ".dat";*/
		      					  
					
					    //ML Testing
					  /*  String  trainFile = path +  "ml_clusteringTrainSetStoredTF.dat";
					    String  testFile  = path +  "ml_clusteringTestSetStoredTF.dat";
					  */
					    
					    //ML Training					    
				/*	    String  trainFile = path +  "ml_clusteringTrainingTrainSetStoredTF.dat";
					    String  testFile  = path +  "ml_clusteringTrainingValidationSetStoredTF.dat";*/
					   
				    	
					  //FT1
				    	
				    	////*****where is Ft.......
//					    String  trainFile  = path  +  "ft_trainSetStoredBothFold1" + (fold)+ ".dat";
//						String  testFile	= path  +  "ft_testSetStoredBothFold1" + (fold)+ ".dat";
//							
				    	String  trainFile  = path  +  "sml_trainSetStoredFold" + (fold)+ ".dat";
						String  testFile	= path  +  "sml_testSetStoredFold" + (fold)+ ".dat";
												
					  /*   String  trainFile   = myPath  + "ft_trainingTrainSetStoredBothFold1" +(fold) + ".dat";   	 
						 String  testFile  = myPath + "ft_trainingValSetStoredBothFold1" +(fold) + ".dat";
			      		*/		  
						
					    
					     //FT5
					/*   String  trainFile  = path  +  "ft_trainSetStoredBothFold5" + (fold)+ ".dat";
						 String  testFile	= path  +  "ft_testSetStoredBothFold5" + (fold)+ ".dat";					
					*/	
					    	
					 /*  String  trainFile   = myPath  + "ft_trainingTrainSetStoredBothFold5" +(fold) + ".dat";   	 
						 String  testFile  = myPath + "ft_trainingValSetStoredBothFold5" +(fold) + ".dat";
				     */ 			
 
				    	
				      String  mainFile	= trainFile;
					
					  allHelper = new MemHelper(mainFile);
					  trainMMh  = new MemHelper(trainFile);
					  testMMh 	= new MemHelper(testFile);	  
					  
					  // trainSVMRegMMh 	= 	getSparseSVMRegressionModel(fold);
					  // trainSVMClassMMh 	= 	getSparseSVMClassModel(fold);
					  // trainNBMMh	 		= 	getSparseNBModel(fold);
					  // trainKNNMMh 		= 	getSparseKNNModel(fold);
					  
					  
					  //User based Filter setting
			           myUserBasedFilter = new FilterAndWeight(trainMMh,1); 		       //with mmh object
			  				 	      
			           //ibcf
			           myItemRec = new ItemItemRecommender(true, 5);
			           
//					   long t1= System.currentTimeMillis();
					  
					       //Make the objects and keep them fixed throughout the program
							  for (int v=5;v<=5;v++)
							  {	  
									
									        singlePass = new SinglePass(trainMMh);	
								 	
						  
					  		 System.out.println("done reading objects");				   	  
							 System.out.println("=====================");
							 System.out.println(" Fold="+ fold);	 	
							 System.out.println("=====================");
					
			
						
					 //Build clusters
				      callKTree (myFlg);										//it is converging after 6-7 iterations	    			   
					    	
				   	//gsu loop, want three results per simThreshold
//					     for(int gsu = START;gsu<=2;gsu++)		
//						 {					    	 
//					        isGraySheepCluserOrAllClusters = gsu;					    
						
							timer.start();		
							// neighbours are here........
							
					    	testWithMemHelper(testMMh,10);
					    	timer.stop();
					    	
							  }
				    }
					 }
   }
//					    	long totalTime= timer.getTime();					    	
//					    	long t2= System.currentTimeMillis(); 	
//					    	
//					    	
//					    	if(gsu ==0 && graySheepSamples !=0)		//to avoid NAN for gsu version
//					    	{					    		
//						    	//calculate results
//						        array_MAE[gsu][foldForGSU-1]		  = MAE;
//						        array_MAEPerUser[gsu][foldForGSU-1]	  = MAEPerUser;				        
//						        array_RMSE[gsu][foldForGSU-1]		  = RMSE;
//						        array_RMSEPerUser[gsu][foldForGSU-1]  = RMSEPerUser;								
//						        array_ROC[gsu][foldForGSU-1]		  = Roc;		        
//							    array_Coverage[gsu][foldForGSU-1]	  = coverage;	
//							    array_GSU[gsu][foldForGSU-1]		  = graySheepUsers;			//gsu and samples
//							    array_GSUSamples[gsu][foldForGSU-1]   = graySheepSamples;
//							    
//							    
//						        // top-N with N=8
//						        for(int x =0;x<8;x++)
//						        {
//							        array_Precision[x][gsu][foldForGSU-1]	= precision[x];
//							        array_Recall[x][gsu][foldForGSU-1]		= recall[x];
//							        array_F1[x][gsu][foldForGSU-1]			= F1[x];
//				
//						        }	
//						        
//						        foldForGSU++;
//						        myTotalFoldsForGSU++;
//					    	}
//					    	
//					    	//for remaining, as it is
//					    	else if (gsu!=0){
//					    		//calculate results
//							        array_MAE[gsu][fold-1]		   = MAE;
//							        array_MAEPerUser[gsu][fold-1]  = MAEPerUser;				        
//							        array_RMSE[gsu][fold-1]		   = RMSE;
//							        array_RMSEPerUser[gsu][fold-1] = RMSEPerUser;
//							        array_ROC[gsu][fold-1]		   = Roc;		        
//								    array_Coverage[gsu][fold-1]	   = coverage;	
//								    array_GSU[gsu][fold-1]		   = graySheepUsers;			//gsu and samples
//								    array_GSUSamples[gsu][fold-1]  = graySheepSamples;
//								    
//								    
//							        // top-N with N=8
//							        for(int x =0;x<8;x++)
//							        {
//								        array_Precision[x][gsu][fold-1]		= precision[x];
//								        array_Recall[x][gsu][fold-1]		= recall[x];
//								        array_F1[x][gsu][fold-1]			= F1[x];
//					
//							        }				    		
//					    	} 						    
//					    	
//					    	  //--------------------------------------------------------------------------------------------------- 	    	
//					    	   System.out.println(" Spheres = " + nSpheres ); 	
//					    	   System.out.print("Coverage="+coverage);
//						   	   System.out.print(",");
//						   	   System.out.print("MAE="+MAE);
//						   	   System.out.print(",");
//						   	   System.out.print("ROC="+Roc);
//						   	   System.out.print(",");  	   
//						   	   System.out.print("\n F1="+F1[3]);
//						   	   System.out.print(",");
//						   	   System.out.print("PRECISION="+precision[3]);
//						   	   System.out.print(",");
//						   	   System.out.print("RECALL="+recall[3]);
//						   	   System.out.print(",");   		
//						   	   System.out.print("\n");
//						   	   
//					    	//if(version ==4)  System.out.println(" alpha =" + (alpha -0.1) + ", beta ="+ (beta+0.1) );
//					    	System.out.println("answered  = "+ totalRecSamples + 
//					    						", nan= "+ totalNan+ ", -ve= "+ totalNegatives);
//					    	System.out.println("gray sheep users="+ graySheepUsers+", gray sheep predictions="+ graySheepSamples);
//					    	System.out.println("------------");
//					    	//---------------------------------------------------------------------------------------------------
//					    						    	
//					    	
//					    	//Write results of each fold in the file 
//						   // WriteResultsInFiles(k, sThr, fold, gsu);
//						    
//					    	//reset variables after each iteration
//					    	totalRecSamples		= 0;
//					    	totalNan			= 0;
//					    	totalNegatives		= 0;
//					    	graySheepSamples	= 0;
//					    	graySheepUsers		= 0;  
//					        
//						 } //end gsu
//							 
//				      }//end of number of iterations
//				  } //end fold					 
//				 
//				    
//					   //gsu loop, want three results per simThreshold
//					     for(int gsu = START;gsu<=2;gsu++)		
//						 {
//					    	 //total folds
//					    	 int totalFolds = 0 ;					    	 
//					    	 if(gsu==0)
//					    		 totalFolds = myTotalFoldsForGSU;
//					    	 else
//					    		 totalFolds = myTotalFolds;
//					    		 					    	 				    	 
//					    
//					        //calculate Means
//					    	 gridResults_Mean_MAE[gsu] 		  	= MEANORSD.calculateMeanOrSD(array_MAE[gsu], totalFolds, 0);
//					    	 gridResults_Mean_MAEPerUser[gsu] 	= MEANORSD.calculateMeanOrSD ( array_MAEPerUser[gsu], totalFolds, 0);
//					    	 gridResults_Mean_RMSE[gsu] 		= MEANORSD.calculateMeanOrSD(array_RMSE[gsu], totalFolds, 0);
//					    	 gridResults_Mean_RMSEPerUser[gsu] 	= MEANORSD.calculateMeanOrSD ( array_RMSEPerUser[gsu], totalFolds, 0);					    
//					    	 gridResults_Mean_RMSE [gsu] 	  	= MEANORSD.calculateMeanOrSD ( array_RMSE[gsu], totalFolds, 0);
//					    	 gridResults_Mean_ROC [gsu] 	  	= MEANORSD.calculateMeanOrSD ( array_ROC[gsu], totalFolds, 0);
//					    	 gridResults_Mean_Coverage [gsu]  	= MEANORSD.calculateMeanOrSD ( array_Coverage[gsu], totalFolds, 0);					 
//					    	 gridResults_Mean_GSU[gsu]	  		= MEANORSD.calculateMeanOrSD ( array_GSU[gsu], totalFolds, 0);
//					    	 gridResults_Mean_GSUSamples[gsu]	= MEANORSD.calculateMeanOrSD ( array_GSUSamples[gsu], totalFolds, 0);
//					    	
//					    	 
//					    	 System.out.println(" gridResults_Mean_GSU[gsu]="+  gridResults_Mean_GSU[gsu]);
//					    	 System.out.println("  gridResults_Mean_GSUSamples[gsu]="+   gridResults_Mean_GSUSamples[gsu]);
//				
//			 
//					    	 //calculate SD
//					    	 gridResults_Sd_MAE[gsu] 		 = MEANORSD.calculateMeanOrSD( array_MAE[gsu], totalFolds, 1);
//					    	 gridResults_Sd_MAEPerUser[gsu]  = MEANORSD.calculateMeanOrSD ( array_MAEPerUser[gsu], totalFolds, 1);
//					    	 gridResults_Sd_RMSE[gsu] 		 = MEANORSD.calculateMeanOrSD  (array_RMSE[gsu], totalFolds, 1);
//					    	 gridResults_Sd_RMSEPerUser[gsu] = MEANORSD.calculateMeanOrSD ( array_RMSEPerUser[gsu], totalFolds, 1);
//					    	 gridResults_Sd_RMSE [gsu] 	     = MEANORSD.calculateMeanOrSD ( array_RMSE[gsu], totalFolds, 1);
//					    	 gridResults_Sd_ROC [gsu] 	     = MEANORSD.calculateMeanOrSD ( array_ROC[gsu], totalFolds, 1);
//					    	 gridResults_Sd_Coverage [gsu]   = MEANORSD.calculateMeanOrSD ( array_Coverage[gsu], totalFolds, 1);
//					    	 gridResults_sd_GSU[gsu]	  	 = MEANORSD.calculateMeanOrSD ( array_GSU[gsu], totalFolds, 1);
//					    	 gridResults_sd_GSUSamples[gsu]	 = MEANORSD.calculateMeanOrSD ( array_GSUSamples[gsu], totalFolds, 1);
//						
//					    	 
//				    		 //topN with N=8
//					    	 for(int x=0;x<8;x++)
//					    	 {
//						    	 gridResults_Mean_Precision[x][gsu]   	=  MEANORSD.calculateMeanOrSD ( array_Precision[x][gsu], totalFolds, 0);
//						    	 gridResults_Mean_Recall[x][gsu] 	 	=  MEANORSD.calculateMeanOrSD ( array_Recall[x][gsu], totalFolds, 0);
//						    	 gridResults_Mean_F1 [x][gsu] 		 	=  MEANORSD.calculateMeanOrSD ( array_F1[x][gsu], totalFolds, 0);
//						    	 
//						    	 gridResults_Sd_Precision[x][gsu]   =  MEANORSD.calculateMeanOrSD ( array_Precision[x][gsu], totalFolds, 1);
//						    	 gridResults_Sd_Recall[x][gsu] 	 	=  MEANORSD.calculateMeanOrSD ( array_Recall[x][gsu], totalFolds, 1);
//						    	 gridResults_Sd_F1 [x][gsu] 		=  MEANORSD.calculateMeanOrSD ( array_F1[x][gsu], totalFolds, 1);
//						    	 
//						    	 
//					    	 }					  					    	
//					    	 	/*System.out.println("gsu="+gsu+", fold="+ totalFolds);					    	
//					    	 	System.out.println("inside, in the 5- fold "+ " gsu users are="+ gridResults_Mean_GSU[gsu]);
//							 	System.out.println("inside, in the 5- fold "+ " gsu samples are="+ gridResults_Mean_GSUSamples[gsu]);
//							 	
//							 	System.out.println("inside, in the 5- fold --> "+ (array_GSU[1][0]+array_GSU[1][1]+array_GSU[1][2]+array_GSU[1][3]+array_GSU[1][4]));
//							 	System.out.println("inside, in the 5- fold--> "+ (array_GSU[2][0]+array_GSU[2][1]+array_GSU[2][2]+array_GSU[2][3]+array_GSU[2][4]));
//							 	
//							 	System.out.println("inside, in the 5- fold, users "+ MEANORSD.calculateMeanOrSD ( array_GSU[gsu], totalFolds, 1));
//							 	System.out.println("inside, in the 5- fold, samples "+ MEANORSD.calculateMeanOrSD ( array_GSUSamples[gsu], totalFolds, 1));
//							 	*/
//						
//							    
//							timer.resetTimer(); 
//					     	myTotalFoldsForGSU = 0;						//reset fold				
//					     	
//					   } //end no. of neighbours for	
//				 	
//   
//   				
//					 }
//   				}
//  			
			
	    	//System.gc();	 
	    
	   }//end of k for

 		//closeFile();
 	
/***************************************************************************************************/
  /************************************************************************************************/
	 
  /**
   * Using RMSE as measurement, this will compare a test set
   * (in MemHelper form) to the results gotten from the recommender
   *  
   * @param testmh the MemHelper with test data in it   //check this what it meant........................Test data?///
   * @return the rmse in comparison to testmh 
   */

  //I have modified this for the three cases:
  //(1) only for gsu (2) for remaining ing users (3) for all users
  
  
  public void testWithMemHelper(MemHelper testmh, int neighbours)     
  {
         	
	  RMSECalculator rmse = new  RMSECalculator();
	 
      IntArrayList users;
	  LongArrayList movies;
	  //IntArrayList coldUsers = coldUsersMMh.getListOfUsers();
	  //IntArrayList coldItems = coldItemsMMh.getListOfMovies();
	  
	  double mov, pred,actual, uAvg;
	  int uid, mid;    		       	
      int totalUsers					= 0;
      IntArrayList userThereInScenario	= new IntArrayList();
      
      		        
      // For each user, make recommendations
      users = testmh.getListOfUsers();
      totalUsers= users.size();
      
//      System.out.println(" List of user in RSME ......"+users);
      
      double uidToPredictions[][] = new double[totalUsers][101]; // 1-49=predictions; 50-99=actual; (Same order); 100=user average
      
      //________________________________________
      
      for (int i = 0; i < totalUsers; i++)        
      {
    	  
      	uid = users.getQuick(i);    
      	      	
      	 int activeClusterID = singlePass.getSphereIDByUID(uid);
		//int activeClusterID = simpleKTree.getClusterIDByUID(uid);
		
	    
//    	boolean go = false;    		
//		if(activeClusterID >= kClusters && isGraySheepCluserOrAllClusters == 0)
//			go =true;    		
//		else if (activeClusterID < kClusters && isGraySheepCluserOrAllClusters==1)
//			go =true;
//		 if (isGraySheepCluserOrAllClusters == 2)
//			go =true;
//		
//		if(go==true)
      	 
      	userThereInScenario.add(uid);
		
    //  	int [] sizeOfCluster=null;
       	{   			 
//       		for(int i1=0; i1<activeClusterID.size();i1++)
//       		{
//				//increase the gray sheep samples, we make prediction for
//				 sizeOfCluster[i1] = singlePass.getSphereSizeByID(activeClusterID.get(i1));
//				//int sizeOfCluster = simpleKTree.getClusterSizeByID(activeClusterID);				
//       		
//				
//				//add user and count them
//				
//				if(isGraySheepCluserOrAllClusters == 0)
//						graySheepUsers = sizeOfCluster[i1];			//if we make separate clusters, then add it rather than assigning	
//				else
//						graySheepUsers++;			
//					
//       		}
	            movies = testmh.getMoviesSeenByUser(uid);
	      
	            for (int j = 0; j < movies.size(); j++)             
	            {	      	
//	            	graySheepSamples++;
//	            	
	            	mid = MemHelper.parseUserOrMovie(movies.getQuick(j));
	             
	              //  if(coldItems.contains(mid))
	                {
	           //     if (mid ==-1)  System.out.println(" rating error--> uid, mid -->" + uid + "," + mid );
	                
	               // double rrr = recommend(uid, mid, blank);                
	              //  double rrr = recommend(uid, mid, neighbours);
	                	
	                //	System.out.println("Calling recommenddSphere....................... " + uid +mid +neighbours);
	                double rrr = recommendSphere(uid, mid, neighbours);
	              //  System.out.println(rrr);
	                
	                /*//Add values to Pair-t
	                if(ImputationMethod ==2)
	                	rmse.addActualToPairT(rrr);
	                else
	                	rmse.addPredToPairT(rrr);
	                */
	                
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
	                            
	                            if(rrr>5 || rrr<-1) {
								} else if(Math.abs(rrr-myRating)<=0.5) {
								} else if(Math.abs(rrr-myRating)<=1.0) {
								} else if (rrr==myRating) {
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
	    		 
      	   		} //end of checking if it is gray sheep cluster etc
			 //} //end of if (cold start checkng) 
	       } //end of user for	   
	    
	        MAE		 	= rmse.mae(); 
	        SDInMAE		= rmse.getMeanErrorOfMAE();
	        SDInROC 	= rmse.getMeanSDErrorOfROC();
	        Roc 		= rmse.getSensitivity();
	        MAEPerUser 	= rmse.maeFinal();
	        RMSE 		= rmse.rmse();
	        RMSEPerUser	= rmse.rmseFinal();
	        coverage	= rmse.getItemCoverage();
	        
	        kMeanEigen_Nmae 	= rmse.nmae_Eigen(1,5);
	        kMeanCluster_Nmae 	= rmse.nmae_ClusterKNNFinal(1, 5);
	        		
	       /* if(ImputationMethod >2)
	        	pValue  = rmse.getPairT();
	        */
	        
	         //-------------------------------------------------
	         //Calculate top-N    		            
	    		
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
      	
   /* System.out.println("totalExtremeErrors="+totalExtremeErrors + ", Total ="+total);
      System.out.println("totalErrorLessThanPoint5="+totalErrorLessThanPoint5 );	       
      System.out.println("totalErrorLessThan1="+totalErrorLessThan1 );
      System.out.println("totalEquals="+totalEquals );  */    		        
      
      //Reset final values
      rmse.resetValues();   
      rmse.resetFinalROC();
      rmse.resetFinalMAE();
      /*if(ImputationMethod >2)
      	rmse.resetPairTPrediction();*/
   
      
      //--------------------------------------------------------------------------------------
      
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

    /**
     * @param int, no. of clusters
     * @param double, simThr
     */
         
    public void WriteResultsInFiles(int k, int sThr, int fold, int myGSU)
    {    	
	 int START = 0;
	 
    	try {			   
			   	  /*//only first time
				   if(k==10 && sThr == -10 && powerUsersThreshold==0 && gsu==2)  //infcat gsu=0 for all results and 2 for finding NoOfClusters results.
				   {
					   //mean
					   writeData1.append("clusters");
				   	   writeData1.append(",");
				   	   writeData1.append("GSU");
				   	   writeData1.append(",");
				   	   writeData1.append("GS Predictions");
				   	   writeData1.append(",");
				   	   writeData1.append("simThr");
				   	   writeData1.append(",");				   	
					   writeData1.append("powerThr");
				   	   writeData1.append(",");
				   	   writeData1.append("gsu");
				   	   writeData1.append(",");
				   	   
				   	   writeData1.append("Coverage");
				   	   writeData1.append(",");
				   	   writeData1.append("MAE");
				   	   writeData1.append(",");
					   writeData1.append("RMSE");
				   	   writeData1.append(",");
				   	   writeData1.append("ROC");
				   	   writeData1.append(",");	
				   	   
				   	   writeData1.append("F1(20)");
				   	   writeData1.append(",");
				   	   writeData1.append("Precision(20)");
				   	   writeData1.append(",");
				   	   writeData1.append("Recall(20)");
				   	   writeData1.append(",");   
				   	   writeData1.append("F1(15)");
				   	   writeData1.append(",");
				   	   writeData1.append("Precision(15)");
				   	   writeData1.append(",");
				   	   writeData1.append("Recall(15)");
				   	   writeData1.append(",");   
				   	   writeData1.append("F1(10)");
				   	   writeData1.append(",");
				   	   writeData1.append("Precision(10)");
				   	   writeData1.append(",");
				   	   writeData1.append("Recall(10)");
				   	   writeData1.append(",");
				   	   writeData1.append("\n");
				   	   
				   	   //sd
					   writeData2.append("clusters");
				   	   writeData2.append(",");
				   	   writeData2.append("GSU");
				   	   writeData2.append(",");
				   	   writeData2.append("GS Predictions");
				   	   writeData2.append(",");
				   	   writeData2.append("simThr");
				   	   writeData2.append(","); 
				   	   writeData2.append("powerThr");
				   	   writeData2.append(",");
				   	   writeData2.append("gsu");
				   	   writeData2.append(",");
					 
				   	   
				   	   writeData2.append("Coverage");
				   	   writeData2.append(",");
				   	   writeData2.append("MAE");
				   	   writeData2.append(",");
					   writeData2.append("RMSE");
				   	   writeData2.append(",");
				   	   writeData2.append("ROC");
				   	   writeData2.append(",");	
				   	   
				   	   writeData2.append("F1(20)");
				   	   writeData2.append(",");
				   	   writeData2.append("Precision(20)");
				   	   writeData2.append(",");
				   	   writeData2.append("Recall(20)");
				   	   writeData2.append(",");   
				   	   writeData2.append("F1(15)");
				   	   writeData2.append(",");
				   	   writeData2.append("Precision(15)");
				   	   writeData2.append(",");
				   	   writeData2.append("Recall(15)");
				   	   writeData2.append(",");   
				   	   writeData2.append("F1(10)");
				   	   writeData2.append(",");
				   	   writeData2.append("Precision(10)");
				   	   writeData2.append(",");
				   	   writeData2.append("Recall(10)");
				   	   writeData2.append(","); 
				   	   writeData2.append("\n");
				    }   */
			   	   
			   
	
				/*   if(gsu==1 || gsu==0)
					   continue;*/
				   
			   if(fold<=5)
			   {				 	
					  
				/*   //start actual writing (Means)
				   writeData1.append(""+kClusters);
			   	   writeData1.append(",");
			   	   writeData1.append(""+ graySheepUsers);
			   	   writeData1.append(",");
			   	   writeData1.append(""+graySheepSamples);
			   	   writeData1.append(",");
			   	   
			   	   writeData1.append(""+simThreshold);
			   	   writeData1.append(",");
			   	   writeData1.append(""+powerUsersThreshold);
			   	   writeData1.append(",");		
			   	   writeData1.append(""+myGSU);
			   	   writeData1.append(",");
			   	   
			   	   writeData1.append(""+coverage);
			   	   writeData1.append(",");
			   	   writeData1.append(""+MAE);
			   	   writeData1.append(",");
			   	   writeData1.append(""+MAEPerUser);
			   	   writeData1.append(",");
			   	   writeData1.append(""+RMSE);
			   	   writeData1.append(",");
			   	   writeData1.append(""+Roc);
			   	   writeData1.append(",");							   	   
			   	   
			   	   writeData1.append(""+F1[3]);
			   	   writeData1.append(",");
			   	   writeData1.append(""+precision[3]);
			   	   writeData1.append(",");
			   	   writeData1.append(""+recall[3]);
			   	   writeData1.append(",");   		
		
			   	   
				   writeData1.append(""+F1[2]);
			   	   writeData1.append(",");
			   	   writeData1.append(""+precision[2]);
			   	   writeData1.append(",");
			   	   writeData1.append(""+recall[2]);
			   	   writeData1.append(","); 					   	   
			   	   
				   writeData1.append(""+F1[1]);
			   	   writeData1.append(",");
			   	   writeData1.append(""+precision[1]);
			   	   writeData1.append(",");
			   	   writeData1.append(""+recall[1]);
			   	   writeData1.append(",");   		
			   	   writeData1.append("\n");  */  		    				    
			   	   
				   
			   	   
			   }
			   
			   else
			   {
				  
				   /*writeData1.append("\n");			   	   
				   writeData1.append("---------------------------------------------------------------------------------------");
					*/
				   
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
			   	 
			   	 /*  if(gsu==2){
				   		writeData1.append("\n");
				   		writeData2.append("\n");
			   	   }*/
			   
    	
	   
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
				
				  }//end else
				   //  writeData1.append("\n---------------------------------------------------------------------------------------");
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

   	 try {
   		   //sml
   		   writeData1 = new BufferedWriter(new FileWriter(myPath + "new.csv", true));   			
   		   writeData2 = new BufferedWriter(new FileWriter(myPath + "new.csv", true));	
   	       System.out.println("Rec File Created at"+ "new.csv");
   	       
   	       
   	      /* writeData1 = new BufferedWriter(new FileWriter(myPath + "Results/ft12_110ClusteringMeanResults_50Neigh.csv", true));   			
		   writeData2 = new BufferedWriter(new FileWriter(myPath + "Results/ft12_110ClusteringSdResults_50Neigh.csv", true));	
	       System.out.println("Rec File Created at"+ "Results/Sml22_NoOfClusteringMeanResults_training_50Neigh.csv");     	    
   	       	*/												  
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
    
   	 try {
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