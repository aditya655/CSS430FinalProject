
import java.util.*;
import java.lang.reflect.*;
import java.io.*;

public class Kernel
{
   // Interrupt requests
   public final static int INTERRUPT_SOFTWARE = 1;  // System calls
   public final static int INTERRUPT_DISK     = 2;  // Disk interrupts
   public final static int INTERRUPT_IO       = 3;  // Other I/O interrupts

   // System calls
   public final static int BOOT    =  0; // SysLib.boot( )
   public final static int EXEC    =  1; // SysLib.exec(String args[])
   public final static int WAIT    =  2; // SysLib.join( )
   public final static int EXIT    =  3; // SysLib.exit( )
   public final static int SLEEP   =  4; // SysLib.sleep(int milliseconds)
   public final static int RAWREAD =  5; // SysLib.rawread(int blk, byte b[])
   public final static int RAWWRITE=  6; // SysLib.rawwrite(int blk, byte b[])
   public final static int SYNC    =  7; // SysLib.sync( )
   public final static int READ    =  8; // SysLib.cin( )
   public final static int WRITE   =  9; // SysLib.cout( ) and SysLib.cerr( )

   // System calls to be added in Assignment 4
   public final static int CREAD   = 10; // SysLib.cread(int blk, byte b[])
   public final static int CWRITE  = 11; // SysLib.cwrite(int blk, byte b[])
   public final static int CSYNC   = 12; // SysLib.csync( )
   public final static int CFLUSH  = 13; // SysLib.cflush( )

   // System calls to be added in Project
   public final static int OPEN    = 14; // SysLib.open( String fileName )
   public final static int CLOSE   = 15; // SysLib.close( int fd )
   public final static int SIZE    = 16; // SysLib.size( int fd )
   public final static int SEEK    = 17; // SysLib.seek( int fd, int offest,
   //              int whence )
   public final static int FORMAT  = 18; // SysLib.format( int files )
   public final static int DELETE  = 19; // SysLib.delete( String fileName )

   // Predefined file descriptors
   public final static int STDIN  = 0;
   public final static int STDOUT = 1;
   public final static int STDERR = 2;

   // Return values
   public final static int OK = 0;
   public final static int ERROR = -1;

   // System thread references
   private static Scheduler scheduler;
   private static Disk disk;
   private static Cache cache;

   // Synchronized Queues
   private static SyncQueue waitQueue;  // for threads to wait for their child
   private static SyncQueue ioQueue;    // I/O queue

   // File System
   private static FileSystem f;

   private final static int COND_DISK_REQ = 1; // wait condition
   private final static int COND_DISK_FIN = 2; // wait condition

   // Standard input
   private static BufferedReader input
      = new BufferedReader( new InputStreamReader( System.in ) );

   // The heart of Kernel
   public static int interrupt( int irq, int cmd, int param, Object args ) {
      TCB myTcb;
      switch( irq ) {
         case INTERRUPT_SOFTWARE: // System calls
            switch( cmd ) {
               case BOOT:
                  // instantiate and start a scheduler
                  scheduler = new Scheduler( );
                  scheduler.start( );

                  // instantiate and start a disk
                  disk = new Disk( 1000 );
                  disk.start( );

                  // instantiate a cache memory
                  cache = new Cache( disk.blockSize, 10 );

                  // instantiate synchronized queues
                  ioQueue = new SyncQueue( );
                  waitQueue = new SyncQueue( scheduler.getMaxThreads( ) );

                  // instantiate a file system;
                  f = new FileSystem( 1000 );

                  return OK;
               case EXEC:
                  return sysExec( ( String[] )args );
               case WAIT:
                  if ( ( myTcb = scheduler.getMyTcb( ) ) != null ) {
                     int myTid = myTcb.getTid( ); // get my thread ID
                     return waitQueue.enqueueAndSleep( myTid ); //wait on my tid
                     // woken up by my child thread
                  }
                  return ERROR;
               case EXIT:
                  if ( ( myTcb = scheduler.getMyTcb( ) ) != null ) {
                     int myPid = myTcb.getPid( ); // get my parent ID
                     int myTid = myTcb.getTid( ); // get my ID
                     if ( myPid != -1 ) {
                        // wake up a thread waiting on my parent ID
                        waitQueue.dequeueAndWakeup( myPid, myTid );
                        // I'm terminated!
                        scheduler.deleteThread( );
                        return OK;
                     }
                  }
                  return ERROR;
               case SLEEP:   // sleep a given period of milliseconds
                  scheduler.sleepThread( param ); // param = milliseconds
                  return OK;
               case RAWREAD: // read a block of data from disk
                  while ( disk.read( param, ( byte[] )args ) == false )
                     ioQueue.enqueueAndSleep( COND_DISK_REQ );
                  while ( disk.testAndResetReady( ) == false )
                     ioQueue.enqueueAndSleep( COND_DISK_FIN );
                  return OK;
               case RAWWRITE: // write a block of data to disk
                  while ( disk.write( param, ( byte[] )args ) == false )
                     ioQueue.enqueueAndSleep( COND_DISK_REQ );
                  while ( disk.testAndResetReady( ) == false )
                     ioQueue.enqueueAndSleep( COND_DISK_FIN );
                  return OK;
               case SYNC:     // synchronize disk data to a real file
                  f.sync( ); // allows execution from our file system implementation
                  while ( disk.sync( ) == false )
                     ioQueue.enqueueAndSleep( COND_DISK_REQ );
                  while ( disk.testAndResetReady( ) == false )
                     ioQueue.enqueueAndSleep( COND_DISK_FIN );
                  return OK;
               case READ:
                  switch ( param ) {
                     case STDIN:
                        try {

                           String inputt = input.readLine();
                           if ( input == null ) {
                              return ERROR;
                           }
                           StringBuffer buffer = ( StringBuffer )args;
                           buffer.append( inputt );
                           return inputt.length( );
                        } catch ( IOException e ) {

                           System.out.println( e );
                           return ERROR;
                        }
                     case STDOUT:
                     case STDERR:
                        System.out.println( "threaOS: caused read errors" );
                        return ERROR;
                  }
                  // check if TCB and associated entry are valid before 
                  // passing arguments
                  if ( ( myTcb = scheduler.getMyTcb( ) ) != null ) {
                     FileTableEntry ftEnt = myTcb.getFtEnt( param );
                     if ( ftEnt != null )
                        // arguments: entry, buffer
                        return f.read( ftEnt, ( byte[] )args ); 
                  }
                  return ERROR;
               case WRITE:
                  switch ( param ) {
                     case STDIN:
                        System.out.println( "threaOS: cannot write to System.in" );
                        return ERROR;
                     case STDOUT:
                        System.out.print( (String)args );
                        return OK;
                     case STDERR:
                        System.err.print( (String)args );
                        return OK;
                  }
                  // check if TCB and associated entry are valid before 
                  // passing arguments
                  if ( ( myTcb = scheduler.getMyTcb( ) ) != null ) {
                     FileTableEntry ftEnt = myTcb.getFtEnt( param );
                     if ( ftEnt != null )
                        // arguments: entry, buffer
                        return f.write( ftEnt, ( byte[] )args );
                  }
                  return ERROR;
               case CREAD:   // to be implemented in assignment 4
                  return cache.read( param, ( byte[] )args ) ? OK : ERROR;
               case CWRITE:  // to be implemented in assignment 4
                  return cache.write( param, ( byte[] )args ) ? OK : ERROR;
               case CSYNC:   // to be implemented in assignment 4
                  cache.sync( );
                  return OK;
               case CFLUSH:  // to be implemented in assignment 4
                  cache.flush( );
                  return OK;
               case OPEN:
                  // check if TCB and associated entry are valid before 
                  // passing arguments
                  if ( ( myTcb = scheduler.getMyTcb( ) ) != null ) {
                     String[] s = ( String[] )args;
                     // arguments: filename, mode
                     return myTcb.getFd( f.open( s[0], s[1] ) );
                  } else
                     return ERROR;
               case CLOSE:
                  if ( ( myTcb = scheduler.getMyTcb( ) ) != null ) {
                     FileTableEntry ftEnt = myTcb.getFtEnt( param );
                     if ( ftEnt == null || f.close( ftEnt ) == false )
                        return ERROR;
                     if ( myTcb.returnFd( param ) != ftEnt )
                        return ERROR;
                     return OK;
                  }
                  return ERROR;
               case SIZE:
                  if ( ( myTcb = scheduler.getMyTcb( ) ) != null ) {
                     FileTableEntry ftEnt = myTcb.getFtEnt( param );
                     if ( ftEnt != null )
                        return f.fsize( ftEnt );
                  }
                  return ERROR;
               case SEEK:
                  if ( ( myTcb = scheduler.getMyTcb( ) ) != null ) {
                     int[] seekArgs = ( int[] )args;
                     FileTableEntry ftEnt = myTcb.getFtEnt( param );
                     if ( ftEnt != null )
                        return f.seek( ftEnt, seekArgs[0], seekArgs[1] );
                  }
                  return ERROR;
               case FORMAT:
                  // check for method completion successfully 
                  return ( f.format( param ) == true ) ? OK : ERROR;
               case DELETE:
                  // check for method completion successfully 
                  return ( f.delete( (String)args ) == true ) ? OK : ERROR;
            }
            return ERROR;
         case INTERRUPT_DISK: // Disk interrupts
            // wake up the thread waiting for a service completion
            ioQueue.dequeueAndWakeup( COND_DISK_FIN );

            // wake up the thread waiting for a request acceptance
            ioQueue.dequeueAndWakeup( COND_DISK_REQ );

            return OK;
         case INTERRUPT_IO:   // other I/O interrupts (not implemented)
            return OK;
      }
      return OK;
   }

   // Spawning a new thread
   private static int sysExec( String args[] ) {
      String thrName = args[0]; // args[0] has a thread name
      Object thrObj = null;

      try {
         //get the user thread class from its name
         Class thrClass = Class.forName( thrName );
         if ( args.length == 1 ) // no arguments
            thrObj = thrClass.newInstance( ); // instantiate this class obj
         else {                  // some arguments
            // copy all arguments into thrArgs[] and make a new constructor
            // argument object from thrArgs[]
            String thrArgs[] = new String[ args.length - 1 ];
            for ( int i = 1; i < args.length; i++ )
               thrArgs[i - 1] = args[i];
            Object[] constructorArgs = new Object[] { thrArgs };

            // locate this class object's constructors
            Constructor thrConst
               = thrClass.getConstructor( new Class[] {String[].class} );

            // instantiate this class object by calling this constructor
            // with arguments
            thrObj = thrConst.newInstance( constructorArgs );
         }
         // instantiate a new thread of this object
         Thread t = new Thread( (Runnable)thrObj );

         // add this thread into scheduler's circular list.
         TCB newTcb = scheduler.addThread( t );
         return ( newTcb != null ) ? newTcb.getTid( ) : ERROR;
      }
      catch ( ClassNotFoundException e ) {
         System.out.println( e );
         return ERROR;
      }
      catch ( NoSuchMethodException e ) {
         System.out.println( e );
         return ERROR;
      }
      catch ( InstantiationException e ) {
         System.out.println( e );
         return ERROR;
      }
      catch ( IllegalAccessException e ) {
         System.out.println( e );
         return ERROR;
      }
      catch ( InvocationTargetException e ) {
         System.out.println( e );
         return ERROR;
      }
   }
}
