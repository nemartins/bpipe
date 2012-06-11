/*
* Copyright (c) 2012 MCRI, authors
*
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
*
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package bpipe

import java.util.logging.Logger

/**
 * Continuously monitors files and forwards (or 'tails') their outputs to 
 * specific destinations.  The main purpose of this class is to avoid
 * having all the files be continuously open which is necessary when there
 * are limits on the number of open files.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class Forwarder extends TimerTask {
    
    private static Logger log = Logger.getLogger("bpipe.Forwarder");
    
    /**
     * Global list of all forwarders
     */
    static List<Forwarder> forwarders = []
    
    /**
     * Timer that schedules polling of files that are forwarded from Torque jobs
     */
    static Timer forwardingTimer
   
    /**
     * The list of files that are being 'tailed'
     */
    List<File> files = []
    
    /**
     * Current position in each file that we know about
     */
    Map<File, Long> filePositions = [:]
    
    /**
     * Destinations to which the file should be forwarded
     */
    Map<File, OutputStream> fileDestinations = [:]
    
    Forwarder(File f, OutputStream out) {
        forward(f,out)
    }
   
    void forward(File file, OutputStream out) {
        synchronized(files) {
            files << file
            fileDestinations[file] = out
            filePositions[file] = file.exists()? file.length() : 0L
        }
    }
    
    void cancel(File file) {
        synchronized(files) {
            files.remove(file)
            fileDestinations.remove(file)
            filePositions.remove(file)
        }
    }
    
    @Override
    public void run() {
        List<File> scanFiles
        synchronized(files) {
            try {
                scanFiles = files.clone().grep { it.exists() }
                log.info "Synchronizing ${scanFiles.size()} files"
                byte [] buffer = new byte[8096]
                for(File f in scanFiles) {
                    try {
                        f.withInputStream { ifs ->
                            long skip = filePositions[f]
                            log.info "Skipping $skip characters"
                            ifs.skip(skip)
                            int count = ifs.read(buffer)
                            if(count < 0)
                                return
                            
                            log.info "Read " + count + " chars: " + new String(buffer, 0, count)
                            
                            // TODO: for neater output we could trim the output to the 
                            // most recent newline here
                            fileDestinations[f].write(buffer,0,count)
                            filePositions[f] = filePositions[f] + count
                        }
                    }
                    catch(Exception e) {
                        log.warning "Unable to read file $f"
                        e.printStackTrace()
                    }
                }
            }
            catch(Exception e) {
                log.severe("Failure in output forwarding")
                e.printStackTrace()
            }
        }
    }
}
