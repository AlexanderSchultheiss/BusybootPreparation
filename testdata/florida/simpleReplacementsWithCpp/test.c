#include <stdio.h>

#ifdef NOT_EMPTY

//&begin[calc]

int calc(int a, int b) {
	return a + b;
}

//&end[calc]

#endif

void main() {


#if defined(NOT_EMPTY)
	int toPrint = 5;
//&Line[use_calc]
	toPrint = calc(5, 4);
	
	printf("%d\n", toPrint);
	
#endif
	
	return 0;
}
