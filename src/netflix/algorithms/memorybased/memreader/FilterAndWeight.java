package netflix.algorithms.memorybased.memreader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import netflix.memreader.MemHelper;
import netflix.utilities.Pair;
import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;
import cern.colt.list.LongArrayList;
import cern.colt.map.OpenIntDoubleHashMap;
import cern.colt.map.OpenIntObjectHashMap;

/**
 * A memory-based solution for recommendations for movie data.

 * 
 * For someone using the class, there's only three things you need
 * to know: how to use options, how to recommend, and when to reset.
 * 
 * First, options.  
 * This class actually contains a few memory-based
 * algorithms in one, due to their similarities.  As such, you 
 * need to define which algorithm to use.  This is made easier
 * via the options parameter in the constructor - simply input
 * the constants to define which memory-based algorithm to use.
 * 
 * Note that  correlation, 
 *            vector similarity, 
 *            correlation with default voting,                                      //mutually exclusive.   
 *            and vector similarity with inverse user frequency 
 *            
 *            
 *            Case amplification 
 *            saving weights                                       //can be used with any of these.
 * 
 * 
 * Though it seems like a good idea, I wouldn't use SAVE_WEIGHTS
 * unless you're trying to rank courses for a particular user.
 * 
 * This is because SAVE_WEIGHTS will actually slow the program down
 * if there are too many misses - that is, weights that need to be
 * retrieved.  However, if you're constantly ranking one user
 * in comparison to all others, it should definitely be used as it
 * will be a real time saver.
 * 
 * 
 * Second, recommendations.  
 * Once you've setup the options the 
 * actual recommendation process is a snap.  Just call 
 * recommend(int, int), where the first int is the user id
 * and the second int is the movie id.  It will return its 
 * recommendation.
 * 
 * What can be confusing are some of the results.  If everything
 * goes well, it will return a rating.
 *   
 * If there is absolutely no data to use for recommending (ex, no one has rated the target
 * movie) then it returns -1.  
 * 
 * If the user has already rated the movie that you're trying to predict, it will return -2.
 * 
 *   //but what about if we have diff objects of MemReader.
 *   
 * Third, resetting.  
 * If the underling database (the MemReader)
 * should ever change, you should call reset().  Some of the time
 * saving features stores data, and will not know that the database
 * has changed otherwise. 
 * 
 * @author lewda
 */
/************************************************************************************************************************/
public class FilterAndWeight 
/************************************************************************************************************************/

{
    //Codes for options variable
    public static final int CORRELATION 					= 1;
    public static final int CORRELATION_DEFAULT_VOTING 		= 2;
    public static final int VECTOR_SIMILARITY 				= 4;
    public static final int VS_INVERSE_USER_FREQUENCY 		= 8;
    public static final int CASE_AMPLIFICATION 				= 16;
    public static final int SAVE_WEIGHTS 					= 32;
  
    //we will pass correlation and save weights as option (so it is 1+32 =33)  
    
    
    // Important variables for all processes
    private MemHelper	 mh;
    private int 		 options;
    private int			 whichVersionIsCalled;	// 1 = simple CF, 2-Deviation based, 3- Mixed
    private int 		 thisIsTargetMovie;
    private int 		 pearsonDeviationNotWorking;
    
    
    // Constants for methods - feel free to change them!
    private final double amplifier 	= 2; 								//constant for amplifier - can be changed
    private final int d 			= 2; 								//constant for default voting
    private final int k 			= 10000; 							//constant for default voting
    private final int kd 			= k*d;
    private final int kdd 			= k*d*d;

    
    // Data that gets stored to speed up algorithms
    private HashMap<String, Double> 	savedWeights;
    private OpenIntDoubleHashMap 		vectorNorms;
    private OpenIntDoubleHashMap 		frequencies;
    private OpenIntDoubleHashMap 		stdevs;
    

 /************************************************************************************************************************/

    /**
     * Creates a new FilterAndWeight with a given 
     * MemHelper, using correlation.
     * @param tmh the MemHelper object
     */    

    
    public FilterAndWeight(MemHelper mh) 
    
    {
        this.mh = mh;
        options = CORRELATION;					//by default option is correlation
        setOptions(options);
        
        //whichVersionIsCalled =0;
        pearsonDeviationNotWorking=0;
    }
    
    
  /************************************************************************************************************************/
    /**
     * Creates a new FilterAndWeight with a given MemHelper,
     * using whatever options you want.  The options can
     * be set using the public constants in the class. 
     * 
     * @param tmh the MemHelper object
     * @param options the options to use
     */
 
    
    public FilterAndWeight(MemHelper mh, int options) 
    
    {
        this.mh = mh;
        setOptions(options);
        
    //    whichVersionIsCalled = version;
        pearsonDeviationNotWorking=0;
        
    }

  /************************************************************************************************************************/ 
 /**
  * Set the options we want
  */ 
    
    private void setOptions(int options)    
    {
        this.options = options;
        
        stdevs = new OpenIntDoubleHashMap();				//store--> uid, std
        IntArrayList users = mh.getListOfUsers();
    
        //_____________________________________________________
        
        //go through all the users
        for(int i = 0; i < users.size(); i++)        
        {
          	if((options & CORRELATION) != 0 
                    || (options & CORRELATION_DEFAULT_VOTING) != 0)
            
            	stdevs.put(users.getQuick(i), mh.getStandardDeviationForUser(users.getQuick(i)));
            
            else
                stdevs.put(users.getQuick(i), 1.0);	//if no correlation, std=1.0 against each uid
        }

        //_____________________________________________________
      
        if ((options & SAVE_WEIGHTS) != 0)		//we create a new object to store them
            
        	savedWeights = new HashMap<String, Double>();
       //______________________________________________________
        
        if ((options & VECTOR_SIMILARITY) != 0
                || (options & VS_INVERSE_USER_FREQUENCY) != 0)
    
        	vectorNorms = new OpenIntDoubleHashMap();

       //______________________________________________________
        
        // If using inverse user frequency,
        // Pre-calculate all of the data
        
        if ((options & VS_INVERSE_USER_FREQUENCY) != 0) 		//check them on the paper        
        {
            frequencies = new OpenIntDoubleHashMap();
           
            double numUsers = mh.getNumberOfUsers();
            OpenIntObjectHashMap movies = mh.getMovieToCust();
            
            IntArrayList movieKeys = movies.keys();

            for (int i = 0; i < movieKeys.size(); i++)            
            {  
                frequencies.put(movieKeys.getQuick(i), 
                		Math.log((double) ((LongArrayList) movies.get(movieKeys.getQuick(i))).size())/numUsers); 
            }
        }
    }
    
    /************************************************************************************************************************/
    
    /**
     * This should be run if you change the underlying database.
     */
    
    public void reset() 
    
    {
        setOptions(options);
    }
  
  /************************************************************************************************************************/
  /************************************************************************************************************************/
    
    /**
     * Basic recommendation method for memory-based algorithms.
     * 
     * @param user the user id
     * @param movie the movie id
     * @return the predicted rating, -1 if nothing could be predicted, 
     *          -2 if already rated, or -99 if it fails (mh error)
     */

 /************************************************************************************************************************/
 /************************************************************************************************************************/

 // It gives good results, now we have to see why
    
    public double recommend(int activeUser, int targetMovie, int howMuchNeighbours, int version) 
    
    {
        // If the movie was already rated by the activeUser, return 02
        // If you want more accurate results, return the actual rating
        // (This is done just so that it can tell you what movies to
        // watch, but avoid the ones you have already watched)
    
    	/*
    	 * Basically, it is for movies, which a user have not seen before, 
    	 * (I think, It means, you can divide it into a test set, so in test set
    	 *  nobody has seen movie which we want to predict?)
    	 * 
    	 */
    	
    	whichVersionIsCalled = version;
    	
    	if (mh.getRating(activeUser, targetMovie) > 0) //it can not be the case now, as we are dealing with test and train set separately 
    	
    	{
            return -2;
        }
     
       	
        double currWeight, weightSum = 0, voteSum = 0;
        int uid;

        //But this will return the active user as well?
        LongArrayList users = mh.getUsersWhoSawMovie(targetMovie);		//this should also be done in (total set - test set)
       
        int limit = users.size();
        if (howMuchNeighbours < limit) limit = howMuchNeighbours; 	//by default all
       
        //__________________________________________________
        //go through all users who saw the target movie, to find weights
        
        for (int i = 0; i < limit; i++) 
        
        {
        	//do this if we are using same dataset (without train and test)

        	if(i!=activeUser) //not the active user .... I think they implemented it in such a way so that data are already separated into test and training 
        					  // so if we are calling this function for some user, then he will not be here in the training set	
        	{
            
       		uid = MemHelper.parseUserOrMovie(users.getQuick(i));            
            currWeight = weight(activeUser, uid);				//get weights of two users depending on the similarity function they r using
            weightSum += Math.abs(currWeight);
            
            //why std dev is required?
           
             voteSum += stdevs.get(activeUser) * 
                       (
                        (currWeight *(mh.getRating(uid, targetMovie)- mh.getAverageRatingForUser(uid)))
                        / stdevs.get(uid)
                    	) ;     
            
        	}
        	
        } //end for

        
        // Normalize the sum, such that the unity of the weights is one (K)
        voteSum *= 1.0 / weightSum;
        
        // Add to the average vote for user (rounded) and return
        double answer = mh.getAverageRatingForUser(activeUser) + voteSum;
        
        //This implies that there was no one associated with the current user.
        if (answer == 0 || Double.isNaN(answer))
            return -1;
        else
            return answer;
    }

    
 /************************************************************************************************************************/
    /**
     * Print in how many cases, user avg was same as his rating, so pearson ll not work in that rating
     */
    
    public void printPearsonError()
    {
    	System.out.println("Total cases = "+ pearsonDeviationNotWorking);
    	pearsonDeviationNotWorking=0;
    }
    
/************************************************************************************************************************/
    
    /**
     * @author Musi
     * @param int, active user
     * @param int, target movie
     * @param int, neighbour size
     * @param int, version for sws
     */
  public double recommendS(int activeUser, int targetMovie, int howMuchNeighbours, int version)
  {
	   //System.out.println ("Came in recommendS for ....");
	   
	   thisIsTargetMovie    = targetMovie;
	  
	   double currWeight, weightSum = 0, voteSum = 0, weightSumAbs = 0;
       int uid;

       //But this will return the active user as well?
       LongArrayList users = mh.getUsersWhoSawMovie(targetMovie);		//this should also be done in (total set - test set)
       int limit = users.size();   
         
      // if(limit<=200000)
       {
		     //  System.out.println(users.size() +" Users saw this movie " + targetMovie);
		       
		//        if(limit <=1) return 0;	//filter movies rated by only one guy (Filtr2:) (Check the filters)
		       //__________________________________________________
		       //go through all users who saw the target movie, to find weights
		       
		       OpenIntDoubleHashMap uidToWeight = new  OpenIntDoubleHashMap();
		       IntArrayList myUsers      = new IntArrayList();
		       DoubleArrayList myWeights = new DoubleArrayList();
		       double currentWeight;
		       //__________________________________________________
		       //go through all users who saw the target movie, to find weights
		       
		       //get all weights
		       for (int i = 0; i < limit; i++)       
		       {
		    	    uid = MemHelper.parseUserOrMovie(users.getQuick(i));
		    	    currentWeight  = weight(activeUser, uid);
		    	    uidToWeight.put(uid, currentWeight);
		    	      	    
		       }
		       
		       myUsers 		= uidToWeight.keys();
		       myWeights 	= uidToWeight.values();
		       uidToWeight.pairsSortedByValue(myUsers, myWeights);
		       
		      if (howMuchNeighbours < limit) limit = howMuchNeighbours; 	//by default all
		            
		       int totalNeighbourFound = myUsers.size();
		       double neighRating	   = 0;       
		       
		       for (int i = totalNeighbourFound-1, myTotal=0; i >=0; i--, myTotal++)       
		       {    	   
		       		if(myTotal == limit) break;     	
		       	
		       		uid = myUsers.get(i);       	
		       
		       		//simple
		       		currentWeight= myWeights.get(i);
		           	weightSum += Math.abs(currentWeight);
			    	neighRating = mh.getRating(uid, targetMovie);        
		     	    voteSum+= ( currentWeight* (neighRating  - mh.getAverageRatingForUser(uid))) ;
		     
		       		
		       	/*	   // Taste approach
		            	currentWeight= (myWeights.get(i)+1);
		            	weightSum += Math.abs(currentWeight+1);
		       	    	neighRating = mh.getRating(uid, targetMovie);        
		         	    voteSum+= (currentWeight* (neighRating  - mh.getAverageRatingForUser(uid))) ;
		       	*/
		       		
		           	//Simple, but do not take -ve into accounts
		       /*			currentWeight= (myWeights.get(i));      		
		       			System.out.println(" weight = " + currentWeight);
		       	 
		       		if (currentWeight>0)
		       		{	
		       			weightSum += Math.abs(currentWeight);      		
		           		neighRating = mh.getRating(uid, targetMovie);        
		           		// System.out.println(" neig rating =" + neighRating);
		           		voteSum+= ( currentWeight* (neighRating  - mh.getAverageRatingForUser(uid))) ;
		       		}//end of weight should be positive
		       */
		       		
		       }//end for
		       
		       //System.out.println(" ----------------------------");
		       
		       
		        if(weightSum!=0) voteSum *= 1.0 / weightSum;
		        else{
		        	return 0;					// it will effect the coverage
		        	//return mh.getAverageRatingForUser(activeUser);
		        }
		        
		        double answer = mh.getAverageRatingForUser(activeUser) + voteSum;
		
		      // System.out.println(" prediction =" + answer + ", Weight =" + weightSum);
		       //This implies that there was no one associated with the current user.
		       if (answer <=0)  
		       {
		         // System.out.println(" errror");
		         //   return mh.getAverageRatingForUser(activeUser);
		    	//   return 0;
		       }
		       
		       
		       
		           return answer;     
       	}
       
       //else return -99;
       

    }

  
/************************************************************************************************************************/
  
  public double recommendEnemies(int activeUser, int targetMovie, 
		  							int howMuchNeighbours, int version)
  {

	   double currWeight, weightSum = 0, voteSum = 0, weightSumAbs = 0;
       int uid;

       LongArrayList users = mh.getUsersWhoSawMovie(targetMovie);		//this should also be done in (total set - test set)
       
        int limit = users.size();    
        if(limit <=1) return 0;	//filter movies rated by only one guy (Filtr2:) (Check the filters)
     
       //__________________________________________________
       //go through all users who saw the target movie, to find weights
       
       OpenIntDoubleHashMap uidToWeight = new  OpenIntDoubleHashMap();
       IntArrayList myUsers      = new IntArrayList();
       DoubleArrayList myWeights = new DoubleArrayList();
       double currentWeight;
       
       //__________________________________________________
       //go through all users who saw the target movie, to find weights
       
       //get all weights
       for (int i = 0; i < limit; i++)       
       {
    	    uid = MemHelper.parseUserOrMovie(users.getQuick(i));
    	    currentWeight  = weight(activeUser, uid);
    	    
    	    //We only consider negative weights
    	    if(currentWeight<0)
    	    	uidToWeight.put(uid, currentWeight * (-1)); //make the weight positive
    	      	    
       }
              
       myUsers 		= uidToWeight.keys();
       myWeights 	= uidToWeight.values();
       uidToWeight.pairsSortedByValue(myUsers, myWeights);       
       
       if (howMuchNeighbours < limit) limit = howMuchNeighbours; 	//by default all
            
       int totalNeighbourFound = myUsers.size();
       double neighRating	   = 0;       
       
       for (int i = 0; i <totalNeighbourFound; i++)       
       {    	          			
       		uid = myUsers.get(i);       	
       
       		//simple
       		currentWeight= myWeights.get(i);
           	weightSum += Math.abs(currentWeight);
	    	neighRating = mh.getRating(uid, targetMovie);        
     	
	    	//-----------------------------------------------------------------------
	        // Add or Subtract the active user's avg to the enemy's rating
	        //-----------------------------------------------------------------------

	    	/*double modifiedRating =0;
	    	if(neighRating >=4) 
	    		neighRating-=mh.getAverageRatingForUser(activeUser);
	    	else 
	    		neighRating+=mh.getAverageRatingForUser(activeUser);
	    	*/
	    		    	
	    	voteSum+= ( currentWeight* (neighRating  - mh.getAverageRatingForUser(uid)));
	    	
       		       		
       } //end for
       
       //System.out.println(" ----------------------------");
       
       
       if(weightSum!=0)
    	   voteSum *= 1.0 / weightSum;
       
       double answer = 0;
       
       answer = mh.getAverageRatingForUser(activeUser) + voteSum;
       
       //-----------------------------------------------------------------------
       // Add or Subtract the Extreme
       //-----------------------------------------------------------------------
       
       if(answer>=4) answer = 5 - answer; 
       else 		 answer = 3 + answer; 	
       
       // This implies that there was no one associated with the current user.
       /*if (answer <=0)  
       {
         // System.out.println(" errror");
            return mh.getAverageRatingForUser(activeUser);
    	   //return 0;
       }
       */
       
       
           return answer;     


    }

/************************************************************************************************************************/
  
  public double recommendNP(int activeUser, int targetMovie, 
		  					int howMuchNeighbours, int version,
		  					double alpha, double beta
  							)
  {
	   //System.out.println ("Came in recommendS for ....");
	   
	   thisIsTargetMovie    = targetMovie;
	  
	   double currWeight;
	   double weightSumP = 0, voteSumP = 0;
	   double weightSumN = 0, voteSumN = 0;
       int uid;

       //But this will return the active user as well?
       LongArrayList users = mh.getUsersWhoSawMovie(targetMovie);		//this should also be done in (total set - test set)
       
       int limit = users.size();    
     //  if(limit <=0) return -10;	//filter movies rated by only one guy (Filtr2:) (Check the filters)
       //__________________________________________________
       //go through all users who saw the target movie, to find weights
       
       OpenIntDoubleHashMap uidToWeight  = new  OpenIntDoubleHashMap();
       OpenIntDoubleHashMap uidToWeightN = new  OpenIntDoubleHashMap();
       OpenIntDoubleHashMap uidToWeightP = new  OpenIntDoubleHashMap();
       
       IntArrayList myUsersP      = new IntArrayList();
       DoubleArrayList myWeightsP = new DoubleArrayList();
       IntArrayList myUsersN      = new IntArrayList();
       DoubleArrayList myWeightsN = new DoubleArrayList();
              
       double currentWeight;
       //__________________________________________________
       //go through all users who saw the target movie, to find weights
       
       //get all weights
       for (int i = 0; i < limit; i++)       
       {
    	    uid = MemHelper.parseUserOrMovie(users.getQuick(i));
    	    currentWeight  = weight(activeUser, uid);
    	    
    	    //separate users having +ve and -ve weights
    	    if(currentWeight <0)
    	    	uidToWeightN.put(uid, currentWeight);
    	    
    	    else 
    	    	uidToWeightP.put(uid, currentWeight);
    	      	    
       }
       
       
       myUsersP 	= uidToWeight.keys();
       myWeightsP 	= uidToWeight.values();
       uidToWeightP.pairsSortedByValue(myUsersP, myWeightsP);
       
       myUsersN 	= uidToWeight.keys();
       myWeightsN 	= uidToWeight.values();
       uidToWeightN.pairsSortedByValue(myUsersN, myWeightsN);
       
       
      if (howMuchNeighbours < limit) limit = howMuchNeighbours; 	//by default all
            
       int totalNeighbourFound  = users.size();		//Total
       int totalNNeighbourFound = myUsersN.size();	//Negative Users
       int totalPNeighbourFound = myUsersP.size();	//Positive Users	
       
       double neighRating	   = 0;       
       
       //-----------------
       // +ve neighbours
       //-----------------
       for (int i = totalPNeighbourFound-1, myTotal=0; i >=0; i--, myTotal++)       
       {    	   
    	   //take half users positive and half negative
    	   if(myTotal <(limit *1.0)/2) break;     	
       	
    	   			uid = myUsersP.get(i);
       				
		       		//simple
		       		currentWeight= myWeightsP.get(i);
		           	weightSumP += Math.abs(currentWeight);
			    	neighRating = mh.getRating(uid, targetMovie);        
		     	    voteSumP+= ( currentWeight* (neighRating  - mh.getAverageRatingForUser(uid))) ;
		     
       			
       }// end of positive
       
       //-----------------
       // -ve neighbours
       //-----------------       		
       for (int i = totalNNeighbourFound-1, myTotal=0; i >=0; i--, myTotal++)       
       {    	   
    	   //take half users positive and half negative
    	   if(myTotal <(limit *1.0)/2) break;     	
       	
    	   			uid = myUsersN.get(i);
       				
		       		//simple
		       		currentWeight= myWeightsN.get(i);
		       		currentWeight= currentWeight * -1;		//make it positive
		           	weightSumN += Math.abs(currentWeight);
			    	neighRating = mh.getRating(uid, targetMovie);        
		     	    voteSumN+= ( currentWeight* (neighRating  - mh.getAverageRatingForUser(uid))) ;
		     
       			
       }// end of positive
            
       //System.out.println(" ----------------------------");
       
       if (weightSumP + weightSumN==0)				// If no weight, then it is not able to recommend????
    	   return 0;
       
       //----------------------------------
       // Generate rec
       //----------------------------------
       
        voteSumP *= 1.0 / weightSumP;
        voteSumN *= 1.0 / weightSumN;
        
        double answerP = mh.getAverageRatingForUser(activeUser) + voteSumP;
        double answerN = mh.getAverageRatingForUser(activeUser) - voteSumN;

        double answer = alpha*answerP + beta*answerN;

       if (answer <=0)  
       {
         // System.out.println(" errror");
          //return mh.getAverageRatingForUser(activeUser);
    	   return 0;
       }
       
       
       else
           return answer;     


    }



  /************************************************************************************************************************/
    
    public double recommendProb(int activeUser, int targetMovie, 
  		  					    int howMuchNeighbours, int version,
  		  					    double alpha, double beta
    							)
    {
  	   //System.out.println ("Came in recommendS for ....");
  	   Random rand = new Random();
  	   thisIsTargetMovie    = targetMovie;
  	  
  	   double EPSILON = 1e-6; 
  	   double currWeight;
  	   
  	   double weightSum = 0, voteSum = 0;
  	   double weightSumP = 0, voteSumP = 0;
  	   double weightSumN = 0, voteSumN = 0; 
       int uid;

         //But this will return the active user as well?
         LongArrayList users = mh.getUsersWhoSawMovie(targetMovie);		//this should also be done in (total set - test set)
         
         int limit = users.size();    
         //  if(limit <=0) return -10;	//filter movies rated by only one guy (Filtr2:) (Check the filters)
         //__________________________________________________
         //go through all users who saw the target movie, to find weights
         
         OpenIntDoubleHashMap uidToWeight  = new  OpenIntDoubleHashMap();
         OpenIntDoubleHashMap uidToWeightN = new  OpenIntDoubleHashMap();
         OpenIntDoubleHashMap uidToWeightP = new  OpenIntDoubleHashMap();
         
         IntArrayList myUsersP      = new IntArrayList();
         DoubleArrayList myWeightsP = new DoubleArrayList();
         IntArrayList myUsersN      = new IntArrayList();
         DoubleArrayList myWeightsN = new DoubleArrayList();
         IntArrayList myUsers       = new IntArrayList();
         DoubleArrayList myWeights  = new DoubleArrayList();
         
         
         double currentWeight;
         double probC[]   = new double[5];
         double weightC[] = new double[5];
         
         //__________________________________________________
         //go through all users who saw the target movie, to find weights
         
         //get all weights
         for (int i = 0; i < limit; i++)       
         {
      	    uid = MemHelper.parseUserOrMovie(users.getQuick(i));
      	    currentWeight  = weight(activeUser, uid);
      	    
      	    
      	    //All weights
      	 
 	    	{
     	    	uidToWeight.put(uid, currentWeight);     	    	
     	    	double neighRating = mh.getRating(uid, targetMovie);	    		
	    		int index = (int)(neighRating-1);
	    		
	    		//increase prop
	    		probC[index]++;
	    		if(currentWeight>0) 
	    			weightC[index]+=currentWeight;
   	
 	    	}    	    
      	    
      	    //separate users having +ve and -ve weights    
      	   // -ve weights
      	   
      	    /*   if(currentWeight <0)
      	    	{
	      	    	uidToWeightN.put(uid, currentWeight);
	      	    	
	      	    	double neighRating = mh.getRating(uid, targetMovie);
		    		
		    		int index = (int)(neighRating-1);
		    		
		    		//increase prop
		    		probC[index]++;
		    		weightC[index]+=currentWeight;
	    	
      	    	}
      	    */
      	   
      	   /*else	//+ve weights 
      	    	{
      	    		uidToWeightP.put(uid, currentWeight);
      	    		double neighRating = mh.getRating(uid, targetMovie);
      	    		
      	    		int index = (int)(neighRating-1);
      	    		
      	    		//increase prop
      	    		probC[index]++;
      	    		weightC[index]+=currentWeight;
      	    		
      	    	}
      	    */  	    
         }
         

         //---------------------------------------------------
         //Go through all the classes and find the max of them
         //---------------------------------------------------
         OpenIntDoubleHashMap results = new OpenIntDoubleHashMap();	//from class,prior*likelihood
         
         for(int i=0;i<5;i++)
	         { 
        	 	if(probC[i]==0) probC[i] +=EPSILON;
        	 	
        	 	results.put((i+1),probC[i]*weightC[i]);
        	 	//results.put((i+1),probC[i]);
	         
	         }
         
        	 
         //Add tied cases into it
         IntArrayList tieCases = new IntArrayList();	//Max tie cases will be equal to the no of classes
         
         //Sort the array into ascending order
         IntArrayList myKeys 		= results.keys(); 
         DoubleArrayList myVals 	= results.values();       
         results.pairsSortedByValue(myKeys, myVals);
                
         //last index should have the highest value
         boolean tieFlag =false;
        
         //+ve ties
         for(int i=5-1;i>=0;i--)
         {
      	   if(i>0)
      	   {
      		   if(results.get(5-1) == results.get(i-1)) 
      		   {
      			 tieCases.add(myKeys.get(i-1));		//This index contains value as that of highest result
      			 tieFlag =true;
      		   }
      		   
      	   }
      	   
      	   
         }//end of finding tied cases
        
        
//         //For -VE ties
//         for(int i=1;i<5;i++)
//         {
//      	   if(i<4)
//      	   {
//      		   if(results.get(0) == results.get(i)) 
//      		   {
//      			 tieCases.add(myKeys.get(i));		//This index contains value as that of highest result
//      			 tieFlag =true;
//      		   }
//      		   
//      	   }     	   
//      	   
//         }//end of finding tied cases
//         
        
         //---------------------------
         //Determine the winner index
         //---------------------------
         
         // By Default it should be the last index  in the array and its key corresponds to the class
            int winnerIdx = myKeys.get(5-1);  //for +ve
    
         
         /*//But if tie, then do random break
         if(tieCases.size()>0)
         {
  	       //Break the ties through random return
  	       int randIdx   = rand.nextInt(tieCases.size());
  	       winnerIdx     = tieCases.get(randIdx); 
  	    	   
  	   }        */
        
         //if(3>2) return (double)winnerIdx ;
         
         /*myUsersP 	= uidToWeight.keys();
         myWeightsP 	= uidToWeight.values();
         uidToWeightP.pairsSortedByValue(myUsersP, myWeightsP);
         
         myUsersN 		= uidToWeight.keys();
         myWeightsN 	= uidToWeight.values();
         uidToWeightN.pairsSortedByValue(myUsersN, myWeightsN);
         
         */
        
         myUsers 		= uidToWeight.keys();
         myWeights 		= uidToWeight.values();
         uidToWeight.pairsSortedByValue(myUsers, myWeights);
         
         
         if (howMuchNeighbours < limit) limit = howMuchNeighbours; 	//by default all
              
         int totalNeighbourFound  = users.size();		//Total
         int totalNNeighbourFound = myUsersN.size();	//Negative Users
         int totalPNeighbourFound = myUsersP.size();	//Positive Users	
         
         double neighRating	   = 0;       
        
         //-----------------
         // All neighbours
         //-----------------
         
         for (int i = totalNeighbourFound-1, myTotal=0; i >=0; i--, myTotal++)       
         {    	   
	      	   //take half users positive and half negative
	      	   if(myTotal <limit) break;     	
         	
      	   			uid = myUsers.get(i);
         				
  		       		//simple
  		       		currentWeight= myWeights.get(i);
  		       	
  		       //if(currentWeight >0)
  		       	{
  		       		weightSum += Math.abs(currentWeight);
  			    	neighRating = mh.getRating(uid, targetMovie);        
  		     	    voteSum+= ( currentWeight* (neighRating  - mh.getAverageRatingForUser(uid))) ;
  		       	}
         			
         }// end of all
        
         
         //-----------------
         // +ve neighbours
         //-----------------
                  
         /*
         for (int i = totalPNeighbourFound-1, myTotal=0; i >=0; i--, myTotal++)       
         {    	   
      	   //take half users positive and half negative
      	   if(myTotal <limit) break;     	
         	
      	   			uid = myUsersP.get(i);
         				
  		       		//simple
  		       		currentWeight= myWeightsP.get(i);
  		           	weightSumP += Math.abs(currentWeight);
  			    	neighRating = mh.getRating(uid, targetMovie);        
  		     	    voteSumP+= ( currentWeight* (neighRating  - mh.getAverageRatingForUser(uid))) ;
  		     
         			
         }// end of positive
        */ 
        
         //-----------------
         // -ve neighbours
         //-----------------       		
/*         for (int i = totalNNeighbourFound-1, myTotal=0; i >=0; i--, myTotal++)       
         {    	   
      	   //take half users positive and half negative
      	   if(myTotal <(limit *1.0)/2) break;     	
         	
      	   			uid = myUsersN.get(i);
         				
  		       		//simple
  		       		currentWeight= myWeightsN.get(i);
  		       		currentWeight= currentWeight * -1;		//make it positive
  		           	weightSumN += Math.abs(currentWeight);
  			    	neighRating = mh.getRating(uid, targetMovie);        
  		     	    voteSumN+= ( currentWeight* (neighRating  - mh.getAverageRatingForUser(uid))) ;
  		     
         			
         }// end of positive
*/              
         //System.out.println(" ----------------------------");
         
         if (weightSum==0)				// If no weight, then it is not able to recommend????
      	   return (double)winnerIdx;
         
         //----------------------------------
         // Generate rec
         //----------------------------------
         
          voteSum *= 1.0 / weightSum;
          double answer = mh.getAverageRatingForUser(activeUser) + voteSum;
          
         /*voteSumP *= 1.0 / weightSumP;
          voteSumN *= 1.0 / weightSumN;          
          double answerP = mh.getAverageRatingForUser(activeUser) + voteSumP;
          double answerN = mh.getAverageRatingForUser(activeUser) - voteSumN;
*/
          
         if (answer <=0)  
         {
           // System.out.println(" errror");
            //return mh.getAverageRatingForUser(activeUser);
        	 //return  (double)winnerIdx;
        	 return 0;
         }
         
         
         //Determine confidence
         else {
        	 
        	 if(tieCases.size()>0)
        	 {
	        		 double min = 0;
	        		 int   category = 0;
	        		 
	        		 for(int i=0;i<tieCases.size();i++)
	        		 {
		        		double diff = Math.abs(answer - results.get(4-i));
		        		
		        		if(diff<min) 
		        		{
		        			min = diff;
		        			category =(5-i);
		        		}
		           }
	        		 
	        		 	 return	(alpha *answer + beta * category);
        	 }        			 
            	 if(Math.abs(answer - winnerIdx) <0.10)
            		 	return (double)winnerIdx;

            	 /*        	 if(Math.abs(answer - winnerIdx) <0.30)
        		 return (double)winnerIdx;
        	*/ 
        	 
        	 
        	 else 
        		 return	(alpha *answer + beta *winnerIdx);
         
        	// return answer;
        	      
         	}

      }


/*******************************************************************************************************/

    /**
     * Get Active user's priors in the form of OpenIntDoubleHashMap
     * @param int, uid
     * @param int, classes
     * 
     */
        
        public OpenIntDoubleHashMap getActiveUserPriors(int uid, int classes)
        {
        	 OpenIntDoubleHashMap classToPriors  = new  OpenIntDoubleHashMap();
        	 
        	//Get all movies seen by this user from the training set               
        	 LongArrayList movies = mh.getMoviesSeenByUser(uid); 
             int moviesSize = movies.size();
             double priors[] 	= new double [classes];
             
             
             //Init the priors
             for (int i=0;i<classes;i++)
             	priors[i] =0;
             
             //Calculate the probability that this movie will be in the given class                
             for (int i=0;i<moviesSize;i++)
             {         	
             	   int mid = MemHelper.parseUserOrMovie(movies.getQuick(i));
             	   double rating = mh.getRating(uid, mid);
             	   int index = (int) rating;
             	   
             	  //Find counts for each class
             	   priors[index-1]++;        	         	
             	   
             } 
             
             for(int i=0;i<5;i++)
             {    	 	
        	 		classToPriors.put(i,priors[i]);         
             }
             
             return classToPriors;
        }
  
/*******************************************************************************************************/
        
   public OpenIntDoubleHashMap getPriorWeights(int whichWeight,								//+ve, -ve or both (1,2,3) 
		   										int activeUser, int targetMovie)
   {
   
	   double EPSILON = 1e-6; 
	   double currWeight;
	   
	   double weightSum = 0, voteSum = 0;
	   double weightSumP = 0, voteSumP = 0;
	   double weightSumN = 0, voteSumN = 0; 
	   int uid;

      //But this will return the active user as well?
      LongArrayList users = mh.getUsersWhoSawMovie(targetMovie);		//this should also be done in (total set - test set)
      
      int limit = users.size();    
      
      //__________________________________________________
      //go through all users who saw the target movie, to find weights
      
      OpenIntDoubleHashMap uidToWeight  = new  OpenIntDoubleHashMap();
      OpenIntDoubleHashMap uidToWeightN = new  OpenIntDoubleHashMap();
      OpenIntDoubleHashMap uidToWeightP = new  OpenIntDoubleHashMap();
      OpenIntDoubleHashMap results		= new  OpenIntDoubleHashMap();
      
      IntArrayList myUsersP      = new IntArrayList();
      DoubleArrayList myWeightsP = new DoubleArrayList();
      IntArrayList myUsersN      = new IntArrayList();
      DoubleArrayList myWeightsN = new DoubleArrayList();
      IntArrayList myUsers       = new IntArrayList();
      DoubleArrayList myWeights  = new DoubleArrayList();
      
      
      double currentWeight;
      double probC[]   		= new double[5];
      double weightC[] 		= new double[5];
      double priorWeightC[] = new double[5];
      
      //__________________________________________________
      //go through all users who saw the target movie, to find weights
      
      //get all weights
      for (int i = 0; i < limit; i++)       
      {
   	    uid = MemHelper.parseUserOrMovie(users.getQuick(i));
   	    currentWeight  = weight(activeUser, uid);
   	    
   	    
   	    if (whichWeight==3)		//All weights   	 
	     {
  	    	uidToWeight.put(uid, currentWeight);     	    	
  	    	double neighRating = mh.getRating(uid, targetMovie);	    		
	    	int index = (int)(neighRating-1);
	    		
	    		//increase prop
	    		probC[index]++;
	    		if(currentWeight>0) 
	    			weightC[index]+=currentWeight;
	
	    }    	    
   	    
   	    else if (whichWeight==2)  //-ve weights   	   
   	    {
   	    	if(currentWeight <0)
   	    	{
   	    			uidToWeightN.put(uid, currentWeight);      	    	
	      	    	double neighRating = mh.getRating(uid, targetMovie);		    		
		    		int index = (int)(neighRating-1);
		    		
		    		//increase prop
		    		probC[index]++;
		    		weightC[index]+=currentWeight;
	    	
   	    	}
   	    }
   	   
   	   else	//+ve weights 
   	    	{
   	    		uidToWeightP.put(uid, currentWeight);
   	    		double neighRating = mh.getRating(uid, targetMovie);
   	    		
   	    		int index = (int)(neighRating-1);
   	    		
   	    		//increase prop
   	    		probC[index]++;
   	    		weightC[index]+=currentWeight;
   	    		
   	    	}
      }//end of for
      
      for(int i=0;i<5;i++)
      { 
 	 	if(probC[i]==0) probC[i] +=EPSILON; 	 	
 	 	results.put(i+1, probC[i]*weightC[i]);

      
      }
 
   	    return results;
      }


 /************************************************************************************************************************/

  public double recommendSU(int activeUser, int targetMovie, int howMuchNeighbours, int version)      
   {
 	   whichVersionIsCalled = version;
 	  
     	if (mh.getRating(activeUser, targetMovie) > 0) //it can not be the case now, as we are dealing with test and train set separately 
     	
     	{
     		System.out.println("use is there in train set already");
     		return -2;
         }
      
     	
         double currWeight, weightSum = 0, voteSum = 0;
         int uid;

         //But this will return the active user as well?
         LongArrayList users = mh.getUsersWhoSawMovie(targetMovie);		//this should also be done in (total set - test set)
         
         //__________________________________________________
         //go through all users who saw the target movie, to find weights
         
         int limit = users.size();
         if (howMuchNeighbours < limit) limit = howMuchNeighbours; 	//by default all
          
         /*
         for (int i = 0; i < limit; i++)        
         {
             //find wighted sum	
               
        		uid = MemHelper.parseUserOrMovie(users.getQuick(i));
             currWeight = weight(activeUser, uid);
             weightSum += Math.abs(currWeight);            
         }
           */
         
         
         for (int i = 0; i < limit; i++)       
         {
        	 
         	uid = MemHelper.parseUserOrMovie(users.getQuick(i));
         
         	//if(uid !=activeUser)
         	{
         			currWeight = weight(activeUser, uid);	
         			weightSum += Math.abs(currWeight);
             
         				//simple weighted sum
         				voteSum+= ((currWeight *
             		    (mh.getRating(uid, targetMovie)- mh.getAverageRatingForUser(uid)))) ;
         	}
         	
         } //end for

         // Normalize the sum, such that the unity of the weights is one
         voteSum *= 1.0 / weightSum;
         
          // Add to the average vote for user (rounded) and return
         double answer = mh.getAverageRatingForMovie(targetMovie)+ voteSum;
         //  double answer = mh.getAverageRatingForUser(activeUser) + voteSum;
         
         //This implies that there was no one associated with the current user.
         if (answer == 0 || Double.isNaN(answer))
             return -1;
         else
             return answer;
     }

     
  /************************************************************************************************************************/


  public double recommendH(int activeUser, int targetMovie, int howMuchNeighbours, int version) 
  
  {
	 
  	if (mh.getRating(activeUser, targetMovie) > 0) //it can not be the case now, as we are dealing with test and train set separately 
  	
  	{
          return -2;
      }
   
  	
      double currWeight, weightSum = 0, voteSum = 0;
      int uid;

      //But this will return the active user as well?
      LongArrayList users = mh.getUsersWhoSawMovie(targetMovie);		//this should also be done in (total set - test set)
      
      //__________________________________________________
      //go through all users who saw the target movie, to find weights
      
      for (int i = 0; i < users.size(); i++) 
      
      {
          //find wighted sum	
            
     	  uid = MemHelper.parseUserOrMovie(users.getQuick(i));
          currWeight = weight(activeUser, uid);
          weightSum += Math.abs(currWeight);
          
      }

      
      for (int i = 0; i < users.size(); i++)
     
      {
      	uid = MemHelper.parseUserOrMovie(users.getQuick(i));
      	currWeight = weight(activeUser, uid);	
          
      	//simple weighted sum
           voteSum+= weightSum * 
           	(
           	 (currWeight *(mh.getRating(uid, targetMovie)- mh.getAverageRatingForUser(uid)))
           	 
           	) ;
          
          
              	
      } //end for

       // Add to the average vote for user (rounded) and return
      double answer = mh.getAverageRatingForUser(activeUser) + voteSum;
      
      //This implies that there was no one associated with the current user.
      if (answer == 0 || Double.isNaN(answer))
          return -1;
      else
          return answer;
  }

  
/************************************************************************************************************************/
  

    /**
     * Weights two users, based upon the constructor's options.
     * 
     * @param activeUser
     * @param targetUser
     * @return
     */
    public double weight(int activeUser, int targetUser)     
    {
        double weight = -99;
      
        //__________________________________________________
        
        // If active, sees if this weight is already stored
        if ((options & SAVE_WEIGHTS) != 0)         
        {
            weight = getWeight(activeUser, targetUser);	//we first check if weights are there, fine;
            											//else compute them and store as well(if option of saving is set)
            if (weight != -99)
                return weight;		
        }
        
        //__________________________________________________

        // Use an algorithm to weigh the two users
        if ((options & CORRELATION) != 0)
            weight = correlation(activeUser, targetUser);
        
        else if ((options & CORRELATION_DEFAULT_VOTING) != 0)
            weight = correlationWithDefaultVoting(activeUser, targetUser);
        
        else if ((options & VECTOR_SIMILARITY) != 0 
                || (options & VS_INVERSE_USER_FREQUENCY) != 0 )
            weight = vectorSimilarity(activeUser, targetUser);

        // If using case amplification, amplify the results
        if ((options & CASE_AMPLIFICATION) != 0)
            weight = amplifyCase(weight);

        //______________________________________________________
        // If saving weights, add this new weight to memory
        if ((options & SAVE_WEIGHTS) != 0)
            addWeight(activeUser, targetUser, weight);

        return weight;
    }

/************************************************************************************************************************/
    
    /**
     * Correlation weighting between two users, as provided in "Empirical
     * Analysis of Predictive Algorithms for Collaborative Filtering."
     * 
     * @param mh the database to use
     * @param activeUser the active user
     * @param targetUser the target user
     * @return their correlation
     */
    
    //I have to check them to make sure that they are right or not?, also after
    //sufficient familiarity, may change them to see results
    
    public double correlation(int activeUser, int targetUser)     
    {
    	 int amplifyingFactor = 50;			//give more weight if users have more than 50 movies in common
    	 
    	 double functionResult=0.0;
    	 double topSum, bottomSumActive, bottomSumTarget, rating1, rating2;
         topSum = bottomSumActive = bottomSumTarget = 0;
         
         double activeAvg = mh.getAverageRatingForUser(activeUser);
         double targetAvg = mh.getAverageRatingForUser(targetUser);
         
         ArrayList<Pair> ratings = mh.innerJoinOnMoviesOrRating(activeUser,targetUser, true);
         
         //To check if user average and the rating can be same in some cases
     /*    if(targetUser ==897 || targetUser == 438) 
      * {
        	 System.out.println("--------------------------------------");
        	 System.out.println("User average = "+targetAvg);
        	 LongArrayList myMovies = mh.getMoviesSeenByUser(targetUser);
        	 
        	 for(int i=0;i<myMovies.size();i++)
        		 System.out.println("movie rating ( " +(i+1) +")= "+ mh.parseRating(myMovies.getQuick(i)));
        	 
         }*/
        	 
         // Do the summations
         //_______________________________________________________________
         // for all the common movies
         for (Pair pair : ratings)         
         {
             rating1 = (double) MemHelper.parseRating(pair.a) - activeAvg;
             rating2 = (double) MemHelper.parseRating(pair.b) - targetAvg;
             
             if(rating1==0) pearsonDeviationNotWorking++;
             if(rating2==0) pearsonDeviationNotWorking++;
             
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
        //	if (whichVersionIsCalled==1)  functionResult = (1 * topSum) / Math.sqrt(bottomSumActive * bottomSumTarget);  //why multiply by n?
        //	if (whichVersionIsCalled==2)  functionResult = (n * topSum) / Math.sqrt(bottomSumActive * bottomSumTarget);  //why multiply by n?
        	
        	functionResult = (1 * topSum) / Math.sqrt(bottomSumActive * bottomSumTarget);  //why multiply by n?        	 
        	return  functionResult;
            //return  functionResult * (n/amplifyingFactor); //amplified send        	
        }
        
        else
         // return 1;			// why return 1:?????
        	return 0;			// So in prediction, it will send average back 
    }
    
/************************************************************************************************************************/
    
    /**
     * Adjusted cosine weighting between two items
     * 
     * @param mh the database to use
     * @param activeUser the active item
     * @param targetUser the target item
     * @return their similarity
     */
    
    {
    	
    	
    	
    	
    	
    }
    
    
    
 /************************************************************************************************************************/
    /**
     * Correlation weighting between two users, as provided in "Empirical
     * Analysis of Predictive Algorithms for Collaborative Filtering."
     * 
     * Also uses default voting, which uses a full outer join and adds
     * mythical votes to each user.  (It does work better, trust me.)
     * 
     * @param activeUser the active user id
     * @param targetUser the target user id
     * @return their correlation
     */
    
    private double correlationWithDefaultVoting(int activeUser, int targetUser)    
    {
        int parta, partb, partc, partd, parte, n;
		double rating1;
		double rating2;
        
        ArrayList<Pair> ratings = mh.fullOuterJoinOnMoviesOrRating(activeUser,
                targetUser, true);
        
        parta = partb = partc = partd = parte = 0;
       
        n = ratings.size();

        // Do the summations
        for (Pair pair : ratings)        
        {
            if(pair.a == 0)
                rating1 = d;
            else
                rating1 = MemHelper.parseRating(pair.a);
            
            if(pair.b == 0)
                rating2 = d;
            else
                rating2 = MemHelper.parseRating(pair.b);

            parta += rating1 * rating2;
            partb += rating1;
            partc += rating2;
            partd += Math.pow(rating1, 2);
            parte += Math.pow(rating2, 2);;
        }
        
        //Do some crazy calculations to come up with the correlation
        double answer = ((n+k)*(double)(parta+kdd) - (partb+kd)*(double)(partc+kd)) / 
                Math.sqrt(((n+k)*(double)(partd+kdd) - Math.pow(partb+kd, 2))
                     *((n+k)*(double)(parte+kdd) - Math.pow(partc+kd, 2)));
        
        //In case one student got the same grade all the time, etc.
        if(Double.isNaN(answer))
            return 1;
        else
            return answer;
    }
    
 /************************************************************************************************************************/
    
    /**
     * Treats two users as vectors and find out their cosine similarity.
     * 
     * It can also use inverse user frequency, if VS_INVERSE_USER_FREQUENCY
     * is active.
     * 
     * As described in "Empirical Analysis of Predictive Algorithms 
     * for Collaborative Filtering."
     * 
     * @param activeUser the active user id
     * @param targetUser the target user id
     * @return their similarity
     */

    private double vectorSimilarity(int activeUser, int targetUser)    
    {
    	//int amplifyingFactor =50;
        double bottomActive, bottomTarget, weight;
        LongArrayList ratings;
        
        ArrayList<Pair> commonRatings = mh.innerJoinOnMoviesOrRating(
                activeUser, targetUser, true);
        
        bottomActive = bottomTarget = weight = 0;

        // Find out the bottom portion for summation on active user
        // But what if we are having five fold?... a user may be there in different folds, with
        // Different movie....It is not gonna help
       
     /*   if (vectorNorms.containsKey(activeUser))        
        {
            bottomActive = vectorNorms.get(activeUser);
        }
       */
        
        if(2>3)
        {
        	
        }
        
        else         
        {
            ratings = mh.getMoviesSeenByUser(activeUser);
        
           if ((options & VS_INVERSE_USER_FREQUENCY) == 0)  //simple VS
            {
                for (int i = 0; i < ratings.size(); i++) 
                
                {
                    bottomActive += Math.pow(MemHelper.parseRating(ratings	// sqrt(sum(all movies seen by user(sq(vote))));
                            .getQuick(i)), 2);
                }
            }
            
            else //VS + IUF            
            {
            	//Here we did just one part: f*r^2
                for (int i = 0; i < ratings.size(); i++)                
                {
                    bottomActive += Math.pow(frequencies.get(MemHelper
                            .parseUserOrMovie(ratings.getQuick(i)))
                            * MemHelper.parseRating(ratings.getQuick(i)), 2);
                }
            }
            
            bottomActive = Math.sqrt(bottomActive);
            vectorNorms.put(activeUser, bottomActive);
        }

        
        // Find out the bottom portion for summation on target user
      
        /* if (vectorNorms.containsKey(targetUser))        
        {
            bottomTarget = vectorNorms.get(targetUser);
        }
         */
        
        if(2>3)
        {
        	
        }
        
        else        
        {
            ratings = mh.getMoviesSeenByUser(targetUser);		//all the votes provided by this user
           
            if ((options & VS_INVERSE_USER_FREQUENCY) == 0)	//VS            
            {
                for (int i = 0; i < ratings.size(); i++)             
                {
                    bottomTarget += Math.pow(MemHelper.parseRating(ratings
                            .getQuick(i)), 2);
                }
            }
        
            else // VS + IUF
            {
                for (int i = 0; i < ratings.size(); i++)                 
                {
                    bottomTarget += Math.pow(frequencies.get(MemHelper
                            .parseUserOrMovie(ratings.getQuick(i)))
                            * MemHelper.parseRating(ratings.getQuick(i)), 2);
                }
            }
            
            bottomTarget = Math.sqrt(bottomTarget);
            vectorNorms.put(targetUser, bottomTarget);
        }

        
        // Do the full summation
        if ((options & VS_INVERSE_USER_FREQUENCY) == 0) //VS        
        {
            for (Pair pair : commonRatings)             
            {
                weight += MemHelper.parseRating(pair.a) * MemHelper.parseRating(pair.b);
            }
        }
        
        else 
        { 
            for (Pair pair : commonRatings) 
            {
                weight += (frequencies.get(MemHelper.parseUserOrMovie(pair.a)) * MemHelper
                        .parseRating(pair.a))
                        * (frequencies.get(MemHelper.parseUserOrMovie(pair.b)) * MemHelper
                        .parseRating(pair.b));
            }
        }
        
        weight /= bottomActive * bottomTarget;
        
        return weight;
      //  return (weight * (commonRatings.size()/amplifyingFactor));
    }

 /***********************************************************************************************************************/
    
    /**
     * "Amplifies" any weight, by a constant (defined at top). 
     * 
     * @param weight the weight
     * @return the amplified weight
     */
 
     
    private double amplifyCase(double weight) 
    
    {
        if (weight >= 0)
            return Math.pow(weight, amplifier);
        else
            return -Math.pow(-weight, amplifier);
    }
  
 /************************************************************************************************************************/
    
    /**
     * Saves the weight between two users.
     *  
     * @param user1 
     * @param user2 
     * @param weight 
     */
    private void addWeight(int user1, int user2, double weight) 
    
    {
        savedWeights.put(user1 + ";" + user2, new Double(weight));
    }

/************************************************************************************************************************/
    
    /**
     * Returns a weight if this object has calculated the weight
     * between the two users before.
     * 
     * Returns -99 if there is no weight.
     * @param user1
     * @param user2
     * @return the weight if found, otherwise -99
     */
    private double getWeight(int user1, int user2) 
    
    {
        if(savedWeights.containsKey(user1 + ";" + user2))
            return savedWeights.get(user1 + ";" + user2);

        else if(savedWeights.containsKey(user2 + ";" + user1))
            return savedWeights.get(user2 + ";" + user1);

        return -99;
    }

/************************************************************************************************************************/
    
    /**
     * Prints out the options being used for easy viewing
     * @param options
     */
    public static void printOptions(int options)    
    {
        if ((options & CORRELATION) != 0)
            System.out.print("CORRELATION");
        
        else if ((options & VECTOR_SIMILARITY) != 0)
            System.out.print("VECTOR_SIMILARITY");
        
        else if ((options & CORRELATION_DEFAULT_VOTING) != 0)
            System.out.print("CORRELATION_DEFAULT_VOTING");
        
        else if ((options & VS_INVERSE_USER_FREQUENCY) != 0)
            System.out.print("VS_INVERSE_USER_FREQUENCY");

        if ((options & CASE_AMPLIFICATION) != 0)
            System.out.print(" with CASE_AMPLIFICATION");

        if ((options & SAVE_WEIGHTS) != 0)
            System.out.print(", SAVE_WEIGHTS active");

        System.out.println(".");
    }
}

