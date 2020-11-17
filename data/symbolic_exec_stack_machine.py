#!/usr/bin/env python3
'''
Quick and dirty script to replay MarsAnalytica stack machine execution trace,
gathered with the companion JEB decompiler plugin.

Symbolic variables are introduced in lieu of concrete input characters, and
operations are built over those variables. At the end of execution,
constraints should be visible (or still be in stack depending if the 
trace is complete or not).

Note that the execution trace should have been stripped from the initial
SET operators putting in place concrete input characters.

November 2020 - Joan Calvet - PNF Software
'''

f = open('mars_analytica_stack_machine_trace.log')

def buildOperation(operands, operator, constant):
  '''
    Join operands/constant with provided operator
  '''
  if len(operands) == 1:
    if constant != None:
      return operands[0] + operator + constant
    return operator + operands[0]
  else:
    return operator.join(operands)

# 1. initialize data structures

# fill stack with 'symbolic' variables (ie, characters)
# at the initial offset retrieved from the trace
stack = [None] * 50
charCounter = 0
stack[7] = 'c' + str(charCounter) # S: SET index:7 value:...
charCounter+=1
stack[8] = 'c' + str(charCounter) # S: SET index:8 value:...
charCounter+=1
stack[13] = 'c' + str(charCounter)
charCounter+=1
stack[15] = 'c' + str(charCounter)
charCounter+=1
stack[16] = 'c' + str(charCounter)
charCounter+=1
stack[26] = 'c' + str(charCounter)
charCounter+=1
stack[27] = 'c' + str(charCounter)
charCounter+=1
stack[22] = 'c' + str(charCounter)
charCounter+=1
stack[21] = 'c' + str(charCounter)
charCounter+=1
stack[4] = 'c' + str(charCounter)
charCounter+=1
stack[18] = 'c' + str(charCounter)
charCounter+=1
stack[28] = 'c' + str(charCounter)
charCounter+=1
stack[23] = 'c' + str(charCounter)
charCounter+=1
stack[29] = 'c' + str(charCounter)
charCounter+=1
stack[9] = 'c' + str(charCounter)
charCounter+=1
stack[1] = 'c' + str(charCounter)
charCounter+=1
stack[25] = 'c' + str(charCounter)
charCounter+=1
stack[30] = 'c' + str(charCounter)
charCounter+=1
stack[17] = 'c' + str(charCounter)
charCounter+=1

tempStorage = list() # will serve for poped values
constraints = list()

# 2. parse execution trace
lineCounter = 0
lines = f.readlines()
isTrueConstraint = False
try:
  while lineCounter < len(lines):
    curLine = lines[lineCounter]
    
    if curLine.startswith('S:'):
      print('--------------------------')
      print('> processing %s' % curLine)
      parsed_line = curLine.split()
      operator = parsed_line[1]
      value = parsed_line[2] if len(parsed_line) > 2 else None

      if operator == 'PUSH':
        nextLine = lines[lineCounter+1]
        # we are either pushing the last poped value...
        if len(tempStorage) == 1:
          print('  > pushing unique temporary value')
          stack.append(tempStorage[0])
          tempStorage.clear()
          isTrueConstraint = False # so it was not a constraint
        else:
          # ...or an operation result
          if nextLine.startswith('  | operation: '):
            operator = nextLine[16:17]
            numberOfOperands = nextLine[-3:-2]
            if int(numberOfOperands) == len(tempStorage):
              print('  > building operation with temporary values')
              stack.append('(' + buildOperation(tempStorage,operator,None) + ')')
              tempStorage.clear()
            else:
              raise ValueError('operation without correct number of operands')
          else:
            # ...or a new constant value
            stack.append(value)
            print('  > pushing new value into stack')

      else:

        if isTrueConstraint:
          # a true constraint is a test whose result was not pushed for subsequent operations;
          # it serves to guide execution and these are the ones we want to solve
          print('> found a true constraint:') 
          print(tempStorage)
          constraints.append(tempStorage[0])
          tempStorage.clear()
          isTrueConstraint = False

        if operator == 'SWAP':
          last = stack.pop()
          beforeLast = stack.pop()
          stack.append(last)
          stack.append(beforeLast)
          print('  > stack swapping')

        elif operator == 'GET':
          index = curLine.split(':')[2][:-1]
          tempStorage.append(stack[int(index)])
          print('  > get value in temporary storage')

        elif operator == 'SET':
          index = curLine[curLine.find('index:')+6:curLine.find('value:')-1]
          if len(tempStorage) >= 1:
            stack[int(index)] = tempStorage.pop()
          else:
            raise ValueError('SET problem')
          print('  > set last value from temporary storage')

        elif operator == 'POP':
          value = stack.pop()
          tempStorage.append(value)
          print('  > poped value in temporary storage')

        elif operator == 'TEST':
          operator = curLine[curLine.find('(')+1:curLine.find(',')]
          constante = None
          if curLine.find('cte=') != -1:
            constante = curLine[curLine.find('cte=')+4:-2]

          tempStorage.reverse() # revert operand order (depends on decompiled code, tested for <, >)
          newValue = '(' + buildOperation(tempStorage,operator,constante) + ')'
          tempStorage.clear()
          tempStorage.append(newValue)

          # test result might be a true constraint, 
          # or an intermediary test that will be used for later operations
          isTrueConstraint = True

          print('  > building test operation with temporary value')

        else:
          raise ValueError('not implemented operator')

      print('  > current state')
      print('stack:')
      print(stack)
      print('tempStorage:')
      print(tempStorage)

    lineCounter+=1
except:
  print('> end of emulation (trace incomplete?)')

# 3. final print
print('--- LOGGED CONSTRAINTS --')
for c in constraints:
    print(c)
print('--- STACKED EXPRESSIONS --')
for c in stack:
    if c != None:
      print(c)

