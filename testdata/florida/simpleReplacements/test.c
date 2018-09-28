#include <stdio.h>

//&begin[calc]

int calc(int a, int b) {
	return a + b;
}

//&end[calc]

void main() {

	int toPrint = 5;
//&Line[use_calc]
	toPrint = calc(5, 4);
	
	printf("%d\n", toPrint);
	
	return 0;
}
