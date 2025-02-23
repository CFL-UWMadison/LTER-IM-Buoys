#DONT USE -- Use update_current_TR.R instead 

library(RMySQL)

currentFile <- "/triton/BuoyData/TR/tr_current_limno.csv"

if (file.exists(currentFile)) {
  
  df.A <- read.csv(currentFile,header=F,stringsAsFactors=FALSE)

  nfields <- 4  
  
  ###Assign field names. These must match the database field names
  fields <- c("sampledate","lakeid","watertemp","thermocline_depth")
  ### Assign formatting to each field. Strings (%s) get extra single quotes
  fmt <- c("'%s'","'%s'","%.1f","%.1f")

  ### Assign local variables, must use the same name as database field
  sampledate <- df.A[1,1]
  lakeid <- df.A[1,2]
  watertemp <- df.A[1,3]
  thermocline_depth <- df.A[1,4]

#  if ( is.numeric(thermocline_depth) && !is.na(thermocline_depth) ) {
  
    ###Update the current record, run this after met is added. 
    conn <- dbConnect(MySQL(),dbname="dbmaker", client.flag=CLIENT_MULTI_RESULTS) 
    
    ###Develop the SQL command
    sql <- "update buoy_current_conditions set thermocline_depth="
    tdstr <- sprintf("%.2f",thermocline_depth)
    sql <- paste0(sql,tdstr,", watertemp=")
    wtstr <- sprintf("%.2f",watertemp)
    sql <- paste0(sql,wtstr," where lakeid='",lakeid,"';")
    print(sql)

    result <- dbGetQuery(conn,sql)  
    dbDisconnect(conn)  
#  }#if

}
