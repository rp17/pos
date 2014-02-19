package DAO;

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

my $logger = Log::Log4perl->get_logger('opuma');

my $THIS_YEAR = 2013;

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

sub stub {
    my $self = shift;
    
    my $dbh = $self->{'dbh'};
    
    my $sql = "select * from user where login = ''";
    
    my $sth = $dbh->prepare($sql);
    $sth->execute();
    
    my ($ret, $rc, $rm);
    
    if (my $ref = $sth->fetchrow_hashref()) {
    
    }
    $sth->finish();
    
    $ret->{'returnMessage'} = $rm;
    $ret->{'returnCode'} = $rc;
    
    $dbh->disconnect();
    
    return $ret;
}

sub addUser {
    my $self = shift;
    my $login = shift;
    my $pin = shift;
    my $isAdmin = shift;
    
    my $dbh = $self->{'dbh'};
    
    my $sql = "select count(*) as count from user where login = '$login'";
    
    my $sth = $dbh->prepare($sql);
    $sth->execute();
    
    my ($ret, $rc, $rm);
    
    my $count;
    if (my $ref = $sth->fetchrow_hashref()) {
        $count = $ref->{'count'};
    }
    $sth->finish();
    
    if ($count > 0) {
        $rm = "Login already exists: $login";
        $rc = 1;
    } else {
        $sql = "insert into user (login,pin,is_admin) values (?,?,?)";
        $sth = $dbh->prepare($sql);
        $sth->execute($login,$pin,$isAdmin);
        
        my $ret->{'user_id'} = $dbh->{'mysql_insertid'};
        $ret->{'is_active'} = "Y";
        $ret->{'is_admin'} = $isAdmin;
        $ret->{'login'} = $login;
        $ret->{'pin'} = $pin;
        
        $sth->finish();
        
        $rm = "Success";
        $rc = 0;
    }
    
    $ret->{'returnMessage'} = $rm;
    $ret->{'returnCode'} = $rc;
    
    $dbh->disconnect();
    
    return $ret;
}

sub updateUser {
    my $self = shift;
    my $login = shift;
    my $pin = shift;
    my $isAdmin = shift;
    my $isActive = shift;
    my $id = shift;
    
    $logger->debug("DAO: $id");
    
    my $dbh = $self->{'dbh'};
    
    my $sql;
    if (defined($id)) { 
        $sql = "select user_id from user where login = '$login'";
        $logger->debug("updateUser based on login $login");
    } else {
        $sql = "select user_id from user where user_id = $id";
        $logger->debug("updateUser based on userID $id");
    }
    $logger->debug("SQL: $sql");
    
    my $sth = $dbh->prepare($sql);
    $sth->execute();
    
    my ($ret, $rc, $rm);
    
    my $count;
    if (my $ref = $sth->fetchrow_hashref()) {
        my $id = $sth->{'user_id'};
        $sth->finish();
        
        $sql = "update user set login = ?, pin = ?, is_admin = ?, is_active = ? where user_id = $id";
        $sth = $dbh->prepare($sql);
        $sth->execute($login,$pin,$isAdmin,$isActive);
        
        $ret->{'user_id'} = $id;
        $ret->{'is_active'} = $isActive;
        $ret->{'is_admin'} = $isAdmin;
        $ret->{'login'} = $login;
        $ret->{'pin'} = $pin;
        
        $sth->finish();
        
        $rc = 0;
        $rm = "Success";
    } else {
        $sth->finish();
        $rc = 2;
        $rm = "No user found for login $login";
    }
    
    $ret->{'returnMessage'} = $rm;
    $ret->{'returnCode'} = $rc;
    
    $dbh->disconnect();
    
    return $ret;
}

sub deleteUser {
    my $self = shift;
    my $login = shift;
    
    my $dbh = $self->{'dbh'};
    
    my $sql = "select user_id from user where login = '$login'";
    
    my $sth = $dbh->prepare($sql);
    $sth->execute();
    
    my ($ret, $rc, $rm);
    
    if (my $ref = $sth->fetchrow_hashref()) {
        my $id = $ref->{'user_id'};
        $sth->finish();
        
        $sql = "delete from user where user_id = $id";
        $sth = $dbh->prepare($sql);
        $sth->execute();
        
        $sth->finish();
        
        $rm = "Success";
        $rc = 0;
    } else {
        $sth->finish();
        
        $rm = "User not found for login $login";
        $rc = 2;
    }
    
    $ret->{'returnMessage'} = $rm;
    $ret->{'returnCode'} = $rc;
    
    $dbh->disconnect();
    
    return $ret;
}

sub login {
	my $self = shift;
	my $login = shift;
	my $password = shift;
	
	my $dbh = $self->{'dbh'};

	my $sql = "select * from user where login = '$login'";
	
	my $sth = $dbh->prepare($sql);
	$sth->execute();

	my ($ret, $rc, $rm);

	if (my $ref = $sth->fetchrow_hashref()) {
		if ($ref->{'pin'} eq $password) {
			$rc = 0;
			$rm = "Success";
		} else {
			$rc = 2;
			$rm = "Password doesn't match";
		}
		$ret = $ref;
		$ret->{'pin'} = "****";
	} else {
		$rc = 1;
		$rm = "Login not found: $login"; 
	}
	$sth->finish();
	
	$ret->{'returnMessage'} = $rm;
	$ret->{'returnCode'} = $rc;
	
	$dbh->disconnect();
	
	return $ret;
}