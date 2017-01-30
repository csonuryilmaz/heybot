#!/bin/bash

# Set command line arguments into global variables
while [[ $# -gt 1 ]]
do
    key="$1"
    echo "xxx"
    case $key in
	-u|--user)
	DBUSER="$2"
	shift
	;;
	-p|--pass)
	DBPASS="$2"
	shift
	;;
	-h|--host)
	DBHOST="$2"
	shift
	;;
	-sd|--source-db)
	DB_OLD="$2"
	shift
	;;
	-td|--target-db)
	DB_NEW="$2"
	shift
	;;
	*)
		# unknown option
	;;
    esac
    shift # past argument or value
done;

function openCredentialsFile {
    CREDENTIALS_FILE=mysql-credentials.cnf
    echo "[client]" > $CREDENTIALS_FILE
    echo "user=${DBUSER}" >> $CREDENTIALS_FILE
    echo "password=${DBPASS}" >> $CREDENTIALS_FILE
    echo "host=${DBHOST}" >> $CREDENTIALS_FILE

    DBCONN="--defaults-extra-file=${CREDENTIALS_FILE}"
}

function closeCredentialsFile {
    rm -f $CREDENTIALS_FILE
}

function logCommandLineArguments {
    echo -e "\n"
    echo "-- Arguments:"
    echo "HOST=${DBHOST}"
    echo "USER=${DBUSER}"
    echo "PASS=********"
    echo -e "SOURCE DATABASE=${DB_OLD} \t ->"
    echo -e "TARGET DATABASE=${DB_NEW} \t <-"
}

function cloneTables {
    echo "Begin tables clone (may take a while) ... "
}

function cloneViews {
    echo "Begin views clone (may take a while) ... "
    logCommandLineArguments
    openCredentialsFile

    MYSQL_VIEWS=$(echo "SHOW FULL TABLES WHERE Table_Type = 'VIEW'" | mysql $DBCONN $DB_OLD | tail -n +2 | awk '{ print $1 }');
    echo $MYSQL_VIEWS

    closeCredentialsFile
}

function cloneFunctions {
    echo "Begin functions clone (may take a while) ... "
}

function cloneProcedures {
    echo "Begin procedures clone (may take a while) ... "
}

function cloneTriggers {
    echo "Begin triggers clone (may take a while) ... "
}

echo "Please enter your choice:";
options=("Clone Tables" "Clone Views" "Clone Functions"  "Clone Procedures" "Clone Triggers" "Quit")
select opt in "${options[@]}"
do
    case $opt in
        "Clone Tables")
            cloneTables
	    break
            ;;
        "Clone Views")
            cloneViews
	    break
            ;;
        "Clone Functions")
            cloneFunctions
	    break
            ;;
	"Clone Procedures")
            cloneProcedures
	    break
            ;;
	"Clone Triggers")
            cloneTriggers
	    break
            ;;
        "Quit")
	    echo "Bye"
            break
            ;;
        *) echo invalid option;;
    esac
done