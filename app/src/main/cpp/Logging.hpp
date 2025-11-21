#ifndef __LOGGING_H__
#define __LOGGING_H__

#ifndef	NO_LOGGING
	//#define	NO_LOGGING
#endif

#ifndef __NO_WSLIM_ASSERT_
	#include <assert.h>
	#include <stdlib.h>
#endif

#ifdef NO_LOGGING
	//#include <stdio.h>
#endif

#ifndef BOOL
	typedef int BOOL;
#endif

#ifndef TRUE
	#define TRUE	(1)
#endif

#ifndef FALSE
	#define FALSE	(0)
#endif

#define WS_LOGLEVEL_NONE	0
#define WS_LOGLEVEL_DISP	1
#define WS_LOGLEVEL_INFO	2
#define WS_LOGLEVEL_DEBUG	3
#define WS_LOGLEVEL_WARNING	4
#define WS_LOGLEVEL_ERROR	5
#define WS_LOGLEVEL_CRITIC	6
#define WS_LOGLEVEL_MAX		7
#define WS_LOGLEVEL_CNT		8

/*#ifdef __cplusplus
	//#warning ########## __cplusplus #################################################################
	extern "C" {
#endif*/

#if 0
	#define Logging(x, ...)		printf("[%s %s() %-4d]  \t\033[1;31" x "\033[m", __FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
#endif

	//void SetLogStartTime(void);
	void LogLevelUpDown(int n);
	void SetLogLevelFromEnv(int blLock);
	void SetLogLevel(int iLogLevel, int blLock);
	void SetLogFile(char *lpszLogFilePath);
	char *GetLogFilePath();
	void _Logging(	int iLogLevel, const char *szFileSrc, const char *szFuncName, const int iLine, const char *fmt, ...);
	void _LoggingA(	int iLogLevel, const char *fmt, ...);

#if 0
	void _WsAddAllocMemLink(const char *szFile, int nLine, void *ptr);
	void _WsDelAllocMemLink(void *ptr);
	void _WsLogAllocMem(void);

	#define WsAddPtr(ptr)	_WsAddAllocMemLink(__FILE__, __LINE__, (void *)ptr)
	#define WsDelPtr(ptr)	_WsDelAllocMemLink(ptr)
	#define WsLogMem()	_WsLogAllocMem()
#else
	#define WsAddPtr(ptr)
	#define WsDelPtr(ptr)
	#define WsLogMem()
#endif

#if 0
	void _WsLogAllocMem(void);
	void *_WsMalloc(size_t size, const char *szFile, int nLine);
	void *_WsCalloc(size_t nmemb, size_t size, const char *szFile, int nLine);
	void *_WsRealloc(void *ptr, size_t size, const char *szFile, int nLine);
	char *_WsStrdup(const char *s, const char *szFile, int nLine);
	void _WsFree(void *ptr);
#endif

/*#ifdef __cplusplus
	};
#endif*/

#if 0
	#define WsLogAllocMem()	_WsLogAllocMem()
	#define WsMalloc(x)		_WsMalloc(x, __FILE__, __LINE__)
	#define WsRealloc(x, y)	_WsRealloc(x, y, __FILE__, __LINE__)
	#define WsCalloc(x, y)	_WsCalloc(x, y, __FILE__, __LINE__)
	#define WsStrdup(x)		_WsStrdup(x, __FILE__, __LINE__)
	#define WsFree(x)		_WsFree(x);
#else
	#define WsLogAllocMem()	
	#define WsMalloc(x)		malloc(x)
	#define WsRealloc(x, y)	realloc(x, y)
	#define WsCalloc(x, y)	calloc(x, y)
	#define WsStrdup(x)		strdup(x)
	#define WsFree(x)		free(x)
#endif

#define fLogging(...)			_Logging(WS_LOGLEVEL_DEBUG,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
#define fLoggingNone(...)		_Logging(WS_LOGLEVEL_NONE,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
#define fLoggingDisp(...)		_Logging(WS_LOGLEVEL_DISP,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
#define fLoggingInfo(...)		_Logging(WS_LOGLEVEL_INFO,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
#define fLoggingDebug(...)		_Logging(WS_LOGLEVEL_DEBUG,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
#define fLoggingWarning(...)	_Logging(WS_LOGLEVEL_WARNING,	__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
#define fLoggingError(...)		_Logging(WS_LOGLEVEL_ERROR,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
#define fLoggingCritic(...)		_Logging(WS_LOGLEVEL_CRITIC,	__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
#define fLoggingMax(...)		_Logging(WS_LOGLEVEL_MAX,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
#define fLoggingCnt(...)		_Logging(WS_LOGLEVEL_CNT,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
#define fLoggingA(...)			_LoggingA(WS_LOGLEVEL_DEBUG,	__VA_ARGS__)
#define fLoggingNoneA(...)		_LoggingA(WS_LOGLEVEL_NONE,		__VA_ARGS__)
#define fLoggingDispA(...)		_LoggingA(WS_LOGLEVEL_DISP,		__VA_ARGS__)
#define fLoggingInfoA(...)		_LoggingA(WS_LOGLEVEL_INFO,		__VA_ARGS__)
#define fLoggingDebugA(...)		_LoggingA(WS_LOGLEVEL_DEBUG,	__VA_ARGS__)
#define fLoggingWarningA(...)	_LoggingA(WS_LOGLEVEL_WARNING,	__VA_ARGS__)
#define fLoggingErrorA(...)		_LoggingA(WS_LOGLEVEL_ERROR,	__VA_ARGS__)
#define fLoggingCriticA(...)	_LoggingA(WS_LOGLEVEL_CRITIC,	__VA_ARGS__)
#define fLoggingMaxA(...)		_LoggingA(WS_LOGLEVEL_MAX,		__VA_ARGS__)
#define fLoggingCntA(...)		_LoggingA(WS_LOGLEVEL_CNT,		__VA_ARGS__)

#ifndef NO_LOGGING
	#define Logging(...)			_Logging(WS_LOGLEVEL_DEBUG,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
	#define LoggingNone(...)		_Logging(WS_LOGLEVEL_NONE,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
	#define LoggingDisp(...)		_Logging(WS_LOGLEVEL_DISP,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
	#define LoggingInfo(...)		_Logging(WS_LOGLEVEL_INFO,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
	#define LoggingDebug(...)		_Logging(WS_LOGLEVEL_DEBUG,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
	#define LoggingWarning(...)		_Logging(WS_LOGLEVEL_WARNING,	__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
	#define LoggingError(...)		_Logging(WS_LOGLEVEL_ERROR,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
	#define LoggingCritic(...)		_Logging(WS_LOGLEVEL_CRITIC,	__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
	#define LoggingMax(...)			_Logging(WS_LOGLEVEL_MAX,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)
	#define LoggingCnt(...)			_Logging(WS_LOGLEVEL_CNT,		__FILE__, __FUNCTION__, __LINE__, __VA_ARGS__)

	#define LoggingA(...)			_LoggingA(WS_LOGLEVEL_DEBUG,	__VA_ARGS__)
	#define LoggingNoneA(...)		_LoggingA(WS_LOGLEVEL_NONE,		__VA_ARGS__)
	#define LoggingDispA(...)		_LoggingA(WS_LOGLEVEL_DISP,		__VA_ARGS__)
	#define LoggingInfoA(...)		_LoggingA(WS_LOGLEVEL_INFO,		__VA_ARGS__)
	#define LoggingDebugA(...)		_LoggingA(WS_LOGLEVEL_DEBUG,	__VA_ARGS__)
	#define LoggingWarningA(...)	_LoggingA(WS_LOGLEVEL_WARNING,	__VA_ARGS__)
	#define LoggingErrorA(...)		_LoggingA(WS_LOGLEVEL_ERROR,	__VA_ARGS__)
	#define LoggingCriticA(...)		_LoggingA(WS_LOGLEVEL_CRITIC,	__VA_ARGS__)
	#define LoggingMaxA(...)		_LoggingA(WS_LOGLEVEL_MAX,		__VA_ARGS__)
	#define LoggingCntA(...)		_LoggingA(WS_LOGLEVEL_CNT,		__VA_ARGS__)

	#ifndef __NO_WSLIM_ASSERT_
		#define	Assert(expr)														\
			{																		\
				if (!(expr))														\
				{																	\
					LoggingCnt("Assertion '" __STRING(expr) "' failed !!!!!\n");	\
					assert(expr);													\
				}																	\
			}
	#endif
#else // NO_LOGGING
	#define Logging(...)
	#define LoggingNone(...)
	#define LoggingDisp(...)
	#define LoggingInfo(...)
	#define LoggingDebug(...)
	#define LoggingWarning(...)
	#define LoggingError(...)
	#define LoggingCritic(...)
	#define LoggingMax(...)	
	#define LoggingCnt(...)	

	#define LoggingA(...)
	#define LoggingNoneA(...)
	#define LoggingDispA(...)
	#define LoggingInfoA(...)
	#define LoggingDebugA(...)
	#define LoggingWarningA(...)
	#define LoggingErrorA(...)
	#define LoggingCriticA(...)
	#define LoggingMaxA(...)
	#define LoggingCntA(...)

	#ifndef __NO_WSLIM_ASSERT_
		#define	Assert(expr)																									\
			{																													\
				if (!(expr))																									\
				{																												\
					printf("[%s %s() %-4d] Assertion '" __STRING(expr) "' failed !!!!!\n", __FILE__, __FUNCTION__, __LINE__);	\
					assert(expr);																								\
				}																												\
			}
	#endif
#endif // NO_LOGGING

#define __Unused(x)		(void)(x)

#define WsSafeFree(ptr)												\
	if (ptr)														\
	{																\
		LoggingNone("WsFree( " __STRING(ptr) " = [%p] );\n", ptr);	\
		WsFree(ptr);												\
		ptr = NULL;													\
	}

#define _WsSafeFree(ptr)	\
	if (ptr)				\
	{						\
		WsFree(ptr);		\
		ptr = NULL;			\
	}

#define safeFree(ptr)												\
	if (ptr)														\
	{																\
		LoggingNone("free( " __STRING(ptr) " = [%p] );\n", ptr);	\
		free(ptr);													\
		ptr = NULL;													\
	}

#define _safeFree(ptr)		\
	if (ptr)				\
	{						\
		free(ptr);			\
		ptr = NULL;			\
	}

#define safeDelete(ptr)												\
	if (ptr)														\
	{																\
		LoggingNone("delete " __STRING(ptr) " = [%p];\n", ptr);		\
		delete ptr;													\
		ptr = NULL;													\
	}

#define _safeDelete(ptr)	\
	if (ptr)				\
	{						\
		delete ptr;			\
		ptr = NULL;			\
	}

#define safeDfbRelease(ptr)												\
	if( ptr )															\
	{																	\
		LoggingNone("Release( " __STRING(ptr) " = [%p] );\n", ptr);		\
		ptr->Release(ptr);												\
		ptr = NULL;														\
	}

#define _safeDfbRelease(ptr)	\
	if( ptr )					\
	{							\
		ptr->Release(ptr);		\
		ptr = NULL;				\
	}

#if 0
	#ifdef RDDEBUG
		#define LogAssert(expr)				\
			if (!(expr))					\
			{								\
				LoggingNone("LogAssert");	\
				assert(0);					\
			}
	#else
		#define LogAssert(expr) 	while (0) {}
	#endif

	#define CheckPointer(p, ret)				\
		{										\
			if (p == NULL)						\
			{									\
				LogAssert(0)					\
				LoggingNone(#p " is NULL");		\
				return (ret);					\
			}									\
		}
#endif



#endif // __LOGGING_H__

