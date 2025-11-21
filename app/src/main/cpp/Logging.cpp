#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdarg.h>
#include <time.h>
//#include <pthread.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/ipc.h>
//#include <sys/sem.h>
#include <sys/time.h>

#include "Logging.hpp"

//#define NO_LOG_COLOR

#define DEFAULT_LOG_LEVEL 		WS_LOGLEVEL_NONE

//char *g_lpszLogFilePath = NULL;
char *g_lpszLogFilePath = (char *)"/data/local/tmp/inception_v3/Daree.log";
//char *g_lpszLogFilePath = "/sdcard/Documents/Daree.log";

const char *g_aszLog[]	= { "None",	"Disp", "Info", "DBug", "Warn", "Eror", "Crit", "Maxx", "Cntt" };
//int g_aiColor[]		= { 30,		36,		34,		33,		35,		31,		41,		45,		30 };
int g_aiColorF[]		= { 30,		36,		34,		33,		35,		31,		37,		32,		37 };
int g_aiColorB[]		= { 40,		40,		40,		40,		40,		40,		41,		40,		40 };

//time_t g_nStartMin = 0;

//#define LOGGING_SEMKEY	0xAce3Cafe

#define STD_OUT		stdout

int g_iLogLevel		= DEFAULT_LOG_LEVEL;
//int g_iLoggingSem	= -1;
FILE *g_fpLog		= NULL;

int _InitLog(void);
//int SemGet(int key);

#if 0
void SemLock()
{
	if( g_iLoggingSem < 0 )		g_iLoggingSem = SemGet(LOGGING_SEMKEY);

    struct sembuf pbuf;

    pbuf.sem_num	= 0;
    pbuf.sem_op		= -1;
    pbuf.sem_flg = SEM_UNDO;

    if( semop(g_iLoggingSem, &pbuf, 1) == -1 )		fprintf(STD_OUT, "########## %s() : semop() Error ##########\n", __FUNCTION__);
}

void SemUnlock()
{
    struct sembuf vbuf;

    vbuf.sem_num	= 0;
    vbuf.sem_op		= 1;
    vbuf.sem_flg = SEM_UNDO;

    if( semop(g_iLoggingSem, &vbuf, 1) == -1 )		fprintf(STD_OUT, "########## %s() : semop() Error ##########\n", __FUNCTION__);
}

int SemGet(int key)
{
	int iSem;
	union semun
	{
		int					val;
		struct	semid_ds	*buf;
		unsigned short int	*arrary;
	}  arg;

	if ((iSem = semget( key, 1, IPC_CREAT | 0666 )) == -1 )
	{
		fprintf(STD_OUT, "########## %s() : semget() Error ##########\n", __FUNCTION__);
		return -1;
	}

	arg.val =  1;

	if (semctl(iSem, 0, SETVAL, arg) == -1 )
	{
		fprintf(STD_OUT, "########## %s() : semctl()-SETVAL Error ##########\n", __FUNCTION__);
		return -1;
	}
	return iSem;
}
#endif

#if 0
int SemDel(int iSem)
{
	union semun
	{
		int					val;
		struct	semid_ds	*buf;
		unsigned short int	*arrary;
	}  arg;

	if (semctl(iSem, 0, IPC_RMID, arg) == -1 )
	{
		fprintf(STD_OUT, "########## %s() : semctl()-IPC_RMID Error ##########\n", __FUNCTION__);
		return -1;
	}

	return 0;
}
#endif

void LogLevelUpDown(int n)
{
	int i;

	//SemLock();
	if( g_fpLog == NULL )
	{
		if( _InitLog() )
		{
			//SemUnlock();
			fprintf(STD_OUT, "########## %s() : _InitLog() Fail  ##########\n", __FUNCTION__);
			return;
		}
	}

	i = g_iLogLevel + n;
	if( i < WS_LOGLEVEL_NONE || i > WS_LOGLEVEL_CNT)
	{
		//SemUnlock();
		return;
	}

	g_iLogLevel = i;
	//SemUnlock();
	_Logging(g_iLogLevel, __FILE__, __FUNCTION__, __LINE__, "Changed LogLevel to {%s}\n", g_aszLog[i]);
}

void SetLogLevel(int iLogLevel, int blLock)
{
	if( iLogLevel < WS_LOGLEVEL_NONE || iLogLevel > WS_LOGLEVEL_CNT )	    return;

	//if( blLock )	SemLock();
	if( g_fpLog == NULL )
	{
		if( _InitLog() )
		{
			//if( blLock )	SemUnlock();
			fprintf(STD_OUT, "########## %s() : _InitLog() Fail ##########\n", __FUNCTION__);
			return;
		}
	}

	g_iLogLevel = iLogLevel;
	//if( blLock )	SemUnlock();
}

void SetLogLevelFromEnv(int blLock)
{
	int iLogLevel = DEFAULT_LOG_LEVEL;

	while(TRUE)
	{
		FILE *fpIn = fopen("/etc/config/debug.env", "r");
		if( fpIn == NULL )	break;

		char szLine[1024];
		char *lpStr;

		while( fgets(szLine, 1023, fpIn) )
		{
			if( strchr(szLine, '#') )								continue;
			if( (lpStr = strstr(szLine, "WS_LOG_LEVEL")) == NULL )	continue;
			lpStr += 12;
			if( (lpStr = strchr(lpStr, '=' )) == NULL )				continue;
			lpStr++;
			iLogLevel = atoi(lpStr);
			if( iLogLevel < WS_LOGLEVEL_NONE || iLogLevel > WS_LOGLEVEL_CNT )		iLogLevel = DEFAULT_LOG_LEVEL;
			break;
		}
		fclose(fpIn);
		break;
	}

	SetLogLevel(iLogLevel, blLock);
}

void SetLogFile(char *lpszLogFilePath)
{
	//SemLock();
	if( g_lpszLogFilePath )
	{
		if( g_fpLog )	fclose(g_fpLog);
		free(g_lpszLogFilePath);
	}

	if( lpszLogFilePath )	g_lpszLogFilePath	= strdup(lpszLogFilePath);
	else					g_lpszLogFilePath	= NULL;

	g_fpLog				= NULL;
	_InitLog();
	//SemUnlock();
}

char *GetLogFilePath()
{
	return g_lpszLogFilePath;
}

int _InitLog(void)
{
	if( g_fpLog != NULL )	return 0;

	char *szLogEnv;

	if( g_lpszLogFilePath )
	{
		fprintf(STD_OUT, "**************************************** g_lpszLogFilePath = [%s] ****************************************\n", g_lpszLogFilePath);
		if( ( g_fpLog = fopen(g_lpszLogFilePath, "a+") ) == NULL )		g_lpszLogFilePath = NULL;
	}
	else if( (szLogEnv = getenv("WS_LOG_FIFO")) )
	{
#if 1
		if( mkfifo(szLogEnv, 0666) == 0 )	// comment for stdout log ouput
		{
			g_fpLog = fopen(szLogEnv, "w");
		}
#else
		g_fpLog = fopen(szLogEnv, "a+");
#endif
	}

	if( g_fpLog == NULL )	g_fpLog = STD_OUT;

	if( g_fpLog == NULL )
	{
		fprintf(STD_OUT, "########## %s() : g_fpLog == NULL ##########\n", __FUNCTION__);
		return -1;
	}

	SetLogLevelFromEnv(0);

	//pthread_mutex_init(&g_mutLog, NULL);
	fprintf(STD_OUT, "============= _InitLog ==============================================================\n");
	return 0;
}

#if 0
void SetLogStartTime(void)
{
    struct timeval tv;

    gettimeofday(&tv, NULL);

    time_t nStartMin = tv.tv_sec/60;

	//SemLock();
    g_nStartMin = nStartMin + (timezone/60) - nStartMin % (1440);	// 1440 = 24*60

    while(g_nStartMin > nStartMin)				g_nStartMin -= 1440;
    while( nStartMin - g_nStartMin >= 1440 )	g_nStartMin += 1440;
	//SemUnlock();

    LoggingMax("Start Min. = [%02d:%02d]]\n", (nStartMin - g_nStartMin)/60, (nStartMin - g_nStartMin)%60);
}
#endif

void _Logging(int iLogLevel, const char *szFileSrc, const char *szFuncName, const int iLine, const char *fmt, ...)
{
	va_list ap;
	int nLen, i;
	BOOL blLF = FALSE;
	BOOL blFuncName = FALSE;
	char *szFmt;

	//SemLock();
	if( g_fpLog == NULL )
	{
		if( _InitLog() )
		{
			//SemUnlock();
			fprintf(STD_OUT, "########## %s() : _InitLog() Fail ##########\n", __FUNCTION__);
			return;
		}
	}

	if( iLogLevel < g_iLogLevel || iLogLevel > WS_LOGLEVEL_CNT)
	{
		//SemUnlock();
		return;
	}

	szFmt = strdup(fmt);
	nLen = strlen(szFmt);

	if (nLen > 0)
	{
		for(i = nLen -1; i >= 0; i--)
		{
			if( szFmt[i] != '\n' )	break;

			blLF = TRUE;
			szFmt[i] = 0;
			nLen--;
			continue;
		}
		if (nLen < 4)
		{
			for (i = 0; i < nLen; i++)
			{
				if (szFmt[i] != ' ' && szFmt[i] != '\t' && szFmt[i] != '\n')	break;
			}

			if (i == nLen)	blFuncName = TRUE;
		}
	}
	else
	{
		blFuncName = TRUE;
	}

	struct timeval tv;

	gettimeofday(&tv, NULL);

	//time_t nMin = (tv.tv_sec / 60) - g_nStartMin;
	//time_t nMin = ( ( tv.tv_sec + 32400 ) % 86400 ) / 60;		// KST : + 9 hour
	time_t nMin = ( tv.tv_sec % 86400 ) / 60;		// KST : + 9 hour

	char *lpszFileSrc = (char *)strrchr(szFileSrc, '/');
	if( lpszFileSrc == NULL )	lpszFileSrc = (char *)szFileSrc;
	else						lpszFileSrc++;

#ifdef NO_LOG_COLOR
	fprintf(g_fpLog, "[%02d:%02d:%02d.%06d %s %s() %-4d %s]\t", nMin/60, nMin%60, tv.tv_sec%60, tv.tv_usec, lpszFileSrc, szFuncName, iLine, g_aszLog[iLogLevel]);
#else
	fprintf(g_fpLog, "[%02d:%02d:%02d.%06d \033[1;33;40m%s %s() %-4d\033[m %s]\t\033[1;%d;%dm",
			(int)(nMin/60), (int)(nMin%60), (int)(tv.tv_sec%60), (int)tv.tv_usec, lpszFileSrc, szFuncName, iLine, g_aszLog[iLogLevel], g_aiColorF[iLogLevel], g_aiColorB[iLogLevel]);
#endif

	if( blFuncName )
	{
		fprintf(g_fpLog, "%s()", szFuncName);
	}
	else
	{
		va_start(ap, (const char *)szFmt);
		vfprintf(g_fpLog, szFmt, ap);
		va_end(ap);
	}

#ifndef NO_LOG_COLOR
	fprintf(g_fpLog, "\033[m");
#endif

	if( blLF )	fprintf(g_fpLog, "\n");

	fflush(g_fpLog);

	//SemUnlock();
	free(szFmt);

	//fprintf(STD_OUT, "_Logging >>>\n");
}

void _LoggingA(int iLogLevel, const char *fmt, ...)
{
	va_list ap;

	//SemLock();
	if (iLogLevel < g_iLogLevel || iLogLevel > WS_LOGLEVEL_CNT)
	{
		//SemUnlock();
		return;
	}

	if( g_fpLog == NULL )
	{
		if( _InitLog() )
		{
			//SemUnlock();
			fprintf(STD_OUT, "########## %s() : _InitLog() Fail ##########\n", __FUNCTION__);
			return;
		}
	}

	//fprintf(STD_OUT, "_LoggingA <<<\n");


#ifndef NO_LOG_COLOR
	fprintf(g_fpLog, "\033[1;%d;%dm", g_aiColorF[iLogLevel], g_aiColorB[iLogLevel]);
#endif

	va_start(ap, fmt);
	vfprintf(g_fpLog, fmt, ap);
	va_end(ap);

#ifndef NO_LOG_COLOR
	fprintf(g_fpLog, "\033[m");
#endif

	fflush(g_fpLog);

	//SemUnlock();

	//fprintf(STD_OUT, "_LoggingA >>>\n");
}

#if 0
typedef struct _WsAllocMemLink_t
{
    char	szFile[64];
    int		nLine;
    void	*ptr;
    struct	_WsAllocMemLink_t *lpNext;
} _TWsAllocMemLink;

_TWsAllocMemLink *g_lpWsAllocMemHead = NULL;
_TWsAllocMemLink *g_lpWsAllocMemTail = NULL;

void _WsAddAllocMemLink(const char *szFile, int nLine, void *ptr)
{
	_TWsAllocMemLink *lpAllocMem = malloc(sizeof(_TWsAllocMemLink));

	if( lpAllocMem == NULL )
	{
		fLoggingCritic("!!!!!!!!!!!!!!!!!!!! lpAllocMem = NULL !!!!!!!!!!!!!!!!!!!!\n");
		return;
	}
	lpAllocMem->lpNext = NULL;

	if( g_lpWsAllocMemHead == NULL )
	{
		g_lpWsAllocMemHead = lpAllocMem;
		g_lpWsAllocMemTail = lpAllocMem;
	}
	else
	{
		g_lpWsAllocMemTail->lpNext = lpAllocMem;
		g_lpWsAllocMemTail = lpAllocMem;
	}

	strncpy(lpAllocMem->szFile, szFile, 63);
	lpAllocMem->szFile[63] = 0;
	lpAllocMem->nLine = nLine;
	lpAllocMem->ptr = ptr;
}

void _WsDelAllocMemLink(void *ptr)
{
	_TWsAllocMemLink *lpAllocMem = g_lpWsAllocMemHead;
	_TWsAllocMemLink *lpAllocMemPrev = NULL;

	if( ptr == NULL )
	{
		//fLoggingCritic("!!!!!!!!!!!!!!!!!!!! ptr == NULL !!!!!!!!!!!!!!!!!!!!\n");
		return;
	}

	while(lpAllocMem)
	{
		if( lpAllocMem->ptr == ptr )
		{
			if( lpAllocMemPrev )
			{
				lpAllocMemPrev->lpNext = lpAllocMem->lpNext;
				if( lpAllocMemPrev->lpNext == NULL )	g_lpWsAllocMemTail = lpAllocMemPrev;
			}
			else
			{
				g_lpWsAllocMemHead = lpAllocMem->lpNext;
				if( g_lpWsAllocMemHead == NULL )	g_lpWsAllocMemTail = NULL;
			}
			free(lpAllocMem);
			return;
		}
		lpAllocMemPrev = lpAllocMem;
		lpAllocMem = lpAllocMem->lpNext;
	}
	fLoggingCritic("!!!!!!!!!!!!!!!!!!!! ptr[%p] is not alloc !!!!!!!!!!!!!!!!!!!!\n", ptr);
}

void _WsLogAllocMem(void)
{
	_TWsAllocMemLink *lpAllocMem = g_lpWsAllocMemHead;
	_TWsAllocMemLink *lpAllocMemFree;
	int i = 0;

	if( lpAllocMem == NULL )
	{
		fLoggingCritic("!!!!!!!!!!!!!!!!!!!! Memmory is clear !!!!!!!!!!!!!!!!!!!!\n");
		return;
	}

	fLoggingCritic("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n");
	while(lpAllocMem)
	{
		fLoggingCritic("%-3d: %8p, Line = %-5d, File = [%s]\n", ++i, lpAllocMem->ptr, lpAllocMem->nLine, lpAllocMem->szFile);
		lpAllocMemFree = lpAllocMem;
		lpAllocMem = lpAllocMem->lpNext;
	}
	fLoggingCritic(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n");
}
#endif

#if 0
typedef struct _WsAllocMemLink_t
{
    char			szFile[64];
    int				nLine;
    size_t			nSize;
    void			*ptr;
    struct _WsAllocMemLink_t	*lpNext;
} _TWsAllocMemLink;

_TWsAllocMemLink *g_lpWsAllocMemHead = NULL;
_TWsAllocMemLink *g_lpWsAllocMemTail = NULL;

void _WsAddAllocMemLink(const char *szFile, int nLine, size_t nSize, void *ptr);
void _WsDelAllocMemLink(void *ptr);

void _WsAddAllocMemLink(const char *szFile, int nLine, size_t nSize, void *ptr)
{
	_TWsAllocMemLink *lpAllocMem = malloc(sizeof(_TWsAllocMemLink));

	if( lpAllocMem == NULL )
	{
		fLoggingCritic("!!!!!!!!!!!!!!!!!!!! lpAllocMem = NULL !!!!!!!!!!!!!!!!!!!!\n");
		return;
	}
	lpAllocMem->lpNext = NULL;

	if( g_lpWsAllocMemHead == NULL )
	{
		g_lpWsAllocMemHead = lpAllocMem;
		g_lpWsAllocMemTail = lpAllocMem;
	}
	else
	{
		g_lpWsAllocMemTail->lpNext = lpAllocMem;
		g_lpWsAllocMemTail = lpAllocMem;
	}

	strncpy(lpAllocMem->szFile, szFile, 63);
	lpAllocMem->szFile[63] = 0;
	lpAllocMem->nLine = nLine;
	lpAllocMem->nSize = nSize;
	lpAllocMem->ptr = ptr;
}

void _WsDelAllocMemLink(void *ptr)
{
	_TWsAllocMemLink *lpAllocMem = g_lpWsAllocMemHead;
	_TWsAllocMemLink *lpAllocMemPrev = NULL;

	if( ptr == NULL )
	{
		//fLoggingCritic("!!!!!!!!!!!!!!!!!!!! ptr == NULL !!!!!!!!!!!!!!!!!!!!\n");
		return;
	}

	while(lpAllocMem)
	{
		if( lpAllocMem->ptr == ptr )
		{
			if( lpAllocMemPrev )
			{
				lpAllocMemPrev->lpNext = lpAllocMem->lpNext;
				if( lpAllocMemPrev->lpNext == NULL )	g_lpWsAllocMemTail = lpAllocMemPrev;
			}
			else
			{
				g_lpWsAllocMemHead = lpAllocMem->lpNext;
				if( g_lpWsAllocMemHead == NULL )	g_lpWsAllocMemTail = NULL;
			}
			free(lpAllocMem);
			return;
		}
		lpAllocMemPrev = lpAllocMem;
		lpAllocMem = lpAllocMem->lpNext;
	}
	fLoggingCritic("!!!!!!!!!!!!!!!!!!!! ptr[%p] is not alloc !!!!!!!!!!!!!!!!!!!!\n", ptr);
}

void _WsLogAllocMem(void)
{
	_TWsAllocMemLink *lpAllocMem = g_lpWsAllocMemHead;
	_TWsAllocMemLink *lpAllocMemFree;
	int i = 0;

	if( lpAllocMem == NULL )
	{
		fLoggingCritic("!!!!!!!!!!!!!!!!!!!! Memmory is clear !!!!!!!!!!!!!!!!!!!!\n");
		return;
	}

	fLoggingCritic("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n");
	while(lpAllocMem)
	{
		fLoggingCritic("%-3d: Size = %-5u, Line = %-5d, File = [%s]\n", ++i, lpAllocMem->nSize, lpAllocMem->nLine, lpAllocMem->szFile);
		lpAllocMemFree = lpAllocMem;
		lpAllocMem = lpAllocMem->lpNext;
	}
	fLoggingCritic(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n");
}

void *_WsMalloc(size_t size, const char *szFile, int nLine)
{
	void *ptr = NULL;

	if( (ptr = malloc(size)) )
	{
		_WsAddAllocMemLink(szFile, nLine, size, ptr);
	}
	else
	{
		fLoggingCritic("!!!!!!!!!!!!!!!!!!!! malloc(%d) Fail !!!!!!!!!!!!!!!!!!!!\n", size);
	}
	return ptr;
}

void *_WsCalloc(size_t nmemb, size_t size, const char *szFile, int nLine)
{
	void *ptr = NULL;

	if( (ptr = calloc(nmemb, size)) )
	{
		_WsAddAllocMemLink(szFile, nLine, nmemb*size, ptr);
	}
	else
	{
		fLoggingCritic("!!!!!!!!!!!!!!!!!!!! malloc(%d) Fail !!!!!!!!!!!!!!!!!!!!\n", nmemb*size);
	}
	return ptr;
}

void *_WsRealloc(void *ptr, size_t size, const char *szFile, int nLine)
{
	void *r_ptr;

	if( ptr == NULL )
	{
		fLoggingCritic("!!!!!!!!!!!!!!!!!!!! ptr = NULL !!!!!!!!!!!!!!!!!!!!\n");
	}

	r_ptr = realloc(ptr, size);

	if( r_ptr )
	{
		_WsDelAllocMemLink(ptr);
		_WsAddAllocMemLink(szFile, nLine, size, r_ptr);
	}
	else
	{
		fLoggingCritic("!!!!!!!!!!!!!!!!!!!! realloc(%d) Fail !!!!!!!!!!!!!!!!!!!!\n", size);
	}

	return r_ptr;
}

char *_WsStrdup(const char *s, const char *szFile, int nLine)
{
	char *ptr = NULL;
	if(s)
	{
		if( (ptr = strdup(s)) )
		{
			_WsAddAllocMemLink(szFile, nLine, strlen(s), ptr);
		}
		else
		{
			fLoggingCritic("!!!!!!!!!!!!!!!!!!!! strdup(%s) Fail !!!!!!!!!!!!!!!!!!!!\n", s);
		}
	}
	else
	{
		fLoggingCritic("!!!!!!!!!!!!!!!!!!!! s = NULL !!!!!!!!!!!!!!!!!!!!\n");
	}
	return ptr;
}

void _WsFree(void *ptr)
{
	if( ptr )
	{
		_WsDelAllocMemLink(ptr);
		free(ptr);
	}
	else
	{
		fLoggingCritic("!!!!!!!!!!!!!!!!!!!! ptr = NULL !!!!!!!!!!!!!!!!!!!!\n");
	}
}
#endif
