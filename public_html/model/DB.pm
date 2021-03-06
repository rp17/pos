package DB;

############################################
#
#   Filename:  DAO.pm (package DAO)
#   Author: 
#
############################################

use strict;
use DBI;
use Log::Log4perl;
use Sys::Hostname;
use Data::Dumper;
use String::Util 'trim';
use Constants;

my $logger = Log::Log4perl->get_logger('opuma');

############################################
#
#   Constructor
#
############################################
sub new {
    my $className = shift;
    my $dbh = getConnection();
    my $self = {
        'dbh' => $dbh
    };
    bless $self, $className;
    return $self;
}

############################################
#
#   getConnection()
#
#   Connects to MySQL using the config file
#   named for the host (if found)
#
#   Config file naming convention:
#   host.name.com.db.cfg
#
#   Config entry is name=value;
#   database=myDatabaseName;
#
#   Config keys:
#   database, databaseHost, user, password
#
############################################
sub getConnection {

    my $host = hostname;
    #$logger->debug("HOSTNAME $host");

    my $databaseHost;
    my $database;
    my $user;
    my $password;

    open my $fh, '<', "../cfg/$host.db.cfg" or $logger->error("Can't open $host DB config file");
    if (defined $fh) {
        my $data = do { local $/; <$fh> };
        if ($data =~ /database=(.*);/) {
            $database = $1;
        }
        if ($data =~ /hostname=(.*);/) {
            $databaseHost = $1;
        }
        if ($data =~ /user=(.*);/) {
            $user = $1;
        }
        if ($data =~ /password=(.*);/) {
            $password = $1;
        }
    }

    my $dbh;
    $dbh = DBI->connect("DBI:mysql:host=$databaseHost;database=$database",
                        "$user","$password")
           or $logger->error("Couldn't connect to database: $DBI::errstr");
    die "My script is having problems.  Sorry.  Love, Geoff" unless (defined $dbh);

    return $dbh;
}

sub getSettings {
    my $self = shift;

    my ($ret, $rc, $rm, $ref);
    
    my $dbh = $self->{'dbh'};

    my $sql = "select * from system_settings";
    
    my $sth = $dbh->prepare($sql);
    $sth->execute();
    if ($sth->err() > 0) {
        $ret->{'returnMessage'} = "SQL ERROR: $sql /" . $sth->err() . "/" . $sth->errstr();
        $ret->{'returnCode'} = Constants::ERROR_SQL_ERROR;
        $logger->error("SQL ERROR: $sql /" . $sth->err() . "/" . $sth->errstr());
        return $ret;
    }

    my $a = {};
    while ($ref = $sth->fetchrow_hashref()) {
        $a->{$ref->{'setting_id'}} = $ref->{'setting_value'};
    }
    $sth->finish();
    
    $rm = "Success";
    $rc = 0;
    
    $ret->{'returnMessage'} = $rm;
    $ret->{'returnCode'} = $rc;
    $ret->{'data'} = $a;
    
    $dbh->disconnect();
    
    return $ret;
}