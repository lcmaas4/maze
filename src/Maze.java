import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import javax.swing.JTextField;

import tester.*;
import javalib.impworld.*;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javalib.worldimages.*;
import java.util.Comparator;
import java.util.LinkedList;


// to represent a Cell
class Cell {
	Posn location;
	Color color;
	static int size = (int) ((100 / MazeWorld.MAZE_WIDTH) + (100 / MazeWorld.MAZE_HEIGHT)) + 4;
	List<Edge> edgeList = new ArrayList<Edge>();
	Cell bottom;
	Cell right;
	boolean seen = false;
	int stepsToEnd;

	Cell(Posn location) {
		this.location = location;
		this.color = Color.GRAY;
	}

	// does this Cell have a path to the given Cell?
	public boolean hasPathTo(Cell other) {
		for (Edge e : this.edgeList) {
			if (e.to == other || e.to.hasPathTo(other)) {
				return true;
			}
		}

		return false;
	}

	// draws this Cell
	public WorldImage draw() {
		return new RectangleImage(Cell.size, Cell.size, OutlineMode.SOLID, this.color);
	}

	// EFFECT: Places this tile onto the background at the specified logical
	// coordinates
	public void place(WorldScene drawnBoard) {
		drawnBoard.placeImageXY(this.draw(), this.location.x + Cell.size / 2, this.location.y + Cell.size / 2);

		// drawnBoard.placeImageXY(new TextImage("" + this.stepsToEnd, 12, Color.black),
		// this.location.x + Cell.size / 2, this.location.y + Cell.size / 2);
	}
}

// to compare two Edges based on their weights
class EdgeComparator implements Comparator<Edge> {
	@Override
	public int compare(Edge e1, Edge e2) {
		return e1.weight - e2.weight;
	}
}

// to represent a connection between two Cells
class Edge {
	Cell from;
	Cell to;
	int weight;

	Edge(Cell from, Cell to, int weight) {
		this.from = from;
		this.to = to;
		this.weight = weight;
	}

	// returns the Edge that is the inverse of this Edge
	public Edge getInverse() {
		return new Edge(this.to, this.from, this.weight);
	}

	// public String toString() {
	// return "From: " + from.location.toString() + " to: "
	// + to.location.toString() + ", weight = " + this.weight;
	// }

	// draws a horizontal wall
	public WorldImage drawHorizontalWall() {
		// return new RectangleImage(Cell.size, Cell.size / 10,
		// OutlineMode.SOLID, Color.black);
		return new LineImage(new Posn(Cell.size, 0), Color.black);
	}

	// draws a vertical wall
	public WorldImage drawVerticalWall() {
		// return new RectangleImage(Cell.size / 10, Cell.size,
		// OutlineMode.SOLID, Color.black);
		return new LineImage(new Posn(0, Cell.size), Color.black);
	}

	// places this edge onto the given WorldScene
	public void place(WorldScene drawnBoard) {
		if (this.to == this.from.bottom) {
			drawnBoard.placeImageXY(this.drawHorizontalWall(), (int) this.from.location.x + Cell.size / 2,
					(int) (this.from.location.y + Cell.size));
		} else if (this.to == this.from.right) {
			drawnBoard.placeImageXY(this.drawVerticalWall(), (int) (this.from.location.x + Cell.size),
					(int) this.from.location.y + Cell.size / 2);
		} else {
			throw new IllegalArgumentException("Illegal edge");
		}
	}

	// Does this Edge make progress toward the end of the Maze?
	public boolean continuesGettingCloser() {
		for (Edge e : this.to.edgeList) {
			if (e.to.stepsToEnd < this.to.stepsToEnd) {
				return true;
			}
		}
		return false;
	}
}

// to represent a Maze
class MazeWorld extends World {
	static int MAZE_WIDTH = 30; // # of cells wide the maze is
	static int MAZE_HEIGHT = 30; // # of cells tall the maze is
	List<List<Cell>> cells;
	List<Edge> allEdges;
	List<Edge> master = new ArrayList<Edge>();
	Random rand;
	boolean searchingDFS = false;
	boolean searchingBFS = false;
	Cell searchCurr;
	Cell searchTarget;
	LinkedList<Cell> searchList;
	LinkedList<Cell> currPath;
	LinkedList<Cell> searchPath;
	HashMap<Posn, Posn> representatives;
	boolean generatingPath;
	boolean constructingMaze = true;
	ArrayList<Edge> worklist;
	boolean canSearch = true;
	int bfsSteps = 0;
	int dfsSteps = 0;
	boolean searchedBFS;
	boolean searchedDFS;
	int searchPathLength = 0;
	boolean backtrackingPath = false;
	boolean makeMazeOnTick = false; // switch this to true to watch the walls crash down on each tick

	MazeWorld(Random rand) {
		this.rand = rand;
	}

	MazeWorld() {
		this.rand = new Random();
	}

	MazeWorld(int width, int height) {
		MazeWorld.MAZE_WIDTH = width;
		MazeWorld.MAZE_HEIGHT = height;
	}

	// instantiates each Cell in the Maze
	public void makeCells() {
		this.cells = new ArrayList<List<Cell>>();

		for (int i = 0; i < MazeWorld.MAZE_WIDTH; i++) {
			List<Cell> currList = new ArrayList<Cell>();
			for (int j = 0; j < MazeWorld.MAZE_HEIGHT; j++) {
				currList.add(new Cell(new Posn(i * Cell.size, j * Cell.size)));
			}
			this.cells.add(currList);
		}

		this.cells.get(0).get(0).color = Color.green;
		this.cells.get(MazeWorld.MAZE_WIDTH - 1).get(MazeWorld.MAZE_HEIGHT - 1).color = Color.magenta;
	}

	// connects each Cell in the Maze to its neighbors
	public void initConnections() {
		this.allEdges = new ArrayList<Edge>();

		for (int i = 0; i < MazeWorld.MAZE_WIDTH; i++) {
			for (int j = 0; j < MazeWorld.MAZE_HEIGHT; j++) {
				Cell curr = this.cells.get(i).get(j);

				if (i < MazeWorld.MAZE_WIDTH - 1) {
					Cell right = this.cells.get(i + 1).get(j);

					Edge currToRight = new Edge(curr, right, this.rand.nextInt(100));

					// curr.edgeList.add(currToRight);
					this.allEdges.add(currToRight);

					curr.right = right;
				}

				if (j < MazeWorld.MAZE_HEIGHT - 1) {
					Cell bottom = this.cells.get(i).get(j + 1);

					Edge currToBottom = new Edge(curr, bottom, this.rand.nextInt(100));

					// curr.edgeList.add(currToBottom);
					this.allEdges.add(currToBottom);

					curr.bottom = bottom;
				}
			}
		}
	}

	// draws the maze
	@Override
	public WorldScene makeScene() {
		WorldScene drawnBoard = this.getEmptyScene();
		List<Edge> walls = new ArrayList<Edge>();

		for (List<Cell> list : this.cells) {
			for (Cell c : list) {
				c.place(drawnBoard);
			}
		}

		for (Edge e : this.allEdges) {
			if (!(this.master.contains(e))) {
				walls.add(e);
			}
		}

		for (Edge e : walls) {
			e.place(drawnBoard);
		}

		TextImage dfs;
		TextImage bfs;

		if (this.searchedDFS && !this.searchingDFS && !this.generatingPath) {
			dfs = new TextImage(
					"DFS Steps: " + this.dfsSteps + ", Number of Mistakes: " + (this.dfsSteps - this.searchPathLength),
					12, Color.black);
		}

		else {
			dfs = new TextImage("DFS Steps: " + this.dfsSteps + ", Number of Mistakes: ?", 12, Color.black);
		}

		if (this.searchedBFS && !this.searchingBFS && !this.generatingPath) {
			bfs = new TextImage(
					"BFS Steps: " + this.bfsSteps + ", Number of Mistakes: " + (this.bfsSteps - this.searchPathLength),
					12, Color.black);
		}

		else {
			bfs = new TextImage("BFS Steps: " + this.bfsSteps + ", Number of Mistakes: ?", 12, Color.black);
		}

		drawnBoard.placeImageXY(bfs, MAZE_WIDTH * Cell.size + 150, MAZE_HEIGHT * Cell.size / 2 + 10);
		drawnBoard.placeImageXY(dfs, MAZE_WIDTH * Cell.size + 150, MAZE_HEIGHT * Cell.size / 2 - 10);

		return drawnBoard;
	}

	// finds the path which the given Posn is on in the given HashMap<Posn>
	public Posn find(HashMap<Posn, Posn> map, Posn cellLocation) {
		if (map.get(cellLocation) != cellLocation && map.get(cellLocation) != null) {
			return find(map, map.get(cellLocation));
		} else {
			return cellLocation;
		}
	}

	// unions the paths of the two given Posns in the given HashMap<Posn>
	public void union(HashMap<Posn, Posn> representatives, Posn from, Posn to) {
		representatives.replace(from, to);
	}

	// Is there more than one tree in this HashMap?
	public boolean moreThanOneTree(HashMap<Posn, Posn> representatives) {
		List<Posn> valuesSoFar = new ArrayList<Posn>();

		for (int i = 0; i < MazeWorld.MAZE_WIDTH; i++) {
			for (int j = 0; j < MazeWorld.MAZE_HEIGHT; j++) {
				Posn currKey = this.cells.get(i).get(j).location;
				Posn curr = this.find(representatives, currKey);

				if (!(valuesSoFar.contains(curr))) {
					valuesSoFar.add(curr);
				}

				if (valuesSoFar.size() > 1) {
					return true;
				}
			}
		}
		return false;
	}

	// EFFECT: helper for generating the distances from each Cell to the Maze's end
	public void generateDistancesLoop(Cell c) {
		for (Edge e : c.edgeList) {
			if (e.to.stepsToEnd == 0 && e.to != this.cells.get(MAZE_WIDTH - 1).get(MAZE_HEIGHT - 1)) {
				e.to.stepsToEnd = c.stepsToEnd + 1;
				this.generateDistancesLoop(e.to);
			}
		}
	}

	// EFFECT: generates the distances from each Cell to the Maze's end
	public void generateDistances() {
		Cell end = this.cells.get(MAZE_WIDTH - 1).get(MAZE_HEIGHT - 1);

		end.stepsToEnd = 0;
		for (Edge e : end.edgeList) {
			e.to.stepsToEnd = 1;
			this.generateDistancesLoop(e.to);
		}
	}

	// makes the maze
	public void makeMaze() {
		this.master = new ArrayList<Edge>();
		this.representatives = new HashMap<Posn, Posn>();
		this.worklist = new ArrayList<Edge>();

		for (Edge e : this.allEdges) {
			if (!(this.worklist.contains(e))) {
				this.worklist.add(e);
			}
		}

		this.worklist.sort(new EdgeComparator());

		for (int i = 0; i < MazeWorld.MAZE_WIDTH; i++) {
			for (int j = 0; j < MazeWorld.MAZE_HEIGHT; j++) {
				Cell curr = this.cells.get(i).get(j);

				representatives.put(curr.location, curr.location);
			}
		}
		if (!this.makeMazeOnTick) {
			while (this.moreThanOneTree(representatives) && this.worklist.size() > 0) {
				Edge nextCheapest = this.worklist.get(0);
				Posn from = nextCheapest.from.location;
				Posn to = nextCheapest.to.location;

				if (this.find(representatives, from) == this.find(representatives, to)) {
					this.worklist.remove(0);

				}

				else {
					this.union(representatives, this.find(representatives, from), this.find(representatives, to));
					this.master.add(nextCheapest);
					nextCheapest.from.edgeList.add(nextCheapest);
					nextCheapest.to.edgeList.add(nextCheapest.getInverse());
					this.worklist.remove(0);
				}
			}
			this.generateDistances();
		}
	}

	// makes the maze
	public void makeMazeOnTick() {
		this.master = new ArrayList<Edge>();
		this.representatives = new HashMap<Posn, Posn>();
		this.worklist = new ArrayList<Edge>();

		for (Edge e : this.allEdges) {
			if (!(this.worklist.contains(e))) {
				this.worklist.add(e);
			}
		}

		this.worklist.sort(new EdgeComparator());

		for (int i = 0; i < MazeWorld.MAZE_WIDTH; i++) {
			for (int j = 0; j < MazeWorld.MAZE_HEIGHT; j++) {
				Cell curr = this.cells.get(i).get(j);

				this.representatives.put(curr.location, curr.location);
			}
		}
	}

	// EFFECT: knocks a wall down on each tick, generating the Maze using Kruskal's
	// algorithm
	public void knockWalls() {
		if (this.moreThanOneTree(this.representatives) && this.worklist.size() > 0) {
			Edge nextCheapest = this.worklist.get(0);
			Posn from = nextCheapest.from.location;
			Posn to = nextCheapest.to.location;

			if (this.find(this.representatives, from) == this.find(this.representatives, to)) {
				this.worklist.remove(0);

			}

			else {
				this.union(this.representatives, this.find(this.representatives, from),
						this.find(this.representatives, to));
				this.master.add(nextCheapest);
				nextCheapest.from.edgeList.add(nextCheapest);
				nextCheapest.to.edgeList.add(nextCheapest.getInverse());
				this.worklist.remove(0);
			}
		} else {
			this.constructingMaze = false;
			this.generateDistances();
		}
	}

	public void backtrackPath() {
		if (!this.searchPath.isEmpty()) {
			this.searchPath.remove(0).color = Color.white;
		} else {
			this.backtrackingPath = false;
			// System.out.println("backtracked");
		}
	}

	// EFFECT: generates the (optimal) path from the start to the end of the maze
	public void generatePath() {
		while (!this.searchPath.contains(this.cells.get(MAZE_WIDTH - 1).get(MAZE_HEIGHT - 1))) {
			Cell curr = this.searchPath.get(0);
			for (Edge e : curr.edgeList) {
				if (e.to.stepsToEnd < curr.stepsToEnd && !this.searchPath.contains(e.to)) {
					if (e.continuesGettingCloser() || e.to.stepsToEnd == 0) {
						this.searchPath.addFirst(e.to);
					}
				}
			}
		}
		this.searchPathLength = this.searchPath.size();
	}

	// EFFECT: solves the maze tick-by-tick using DFS
	public void search() {
		if (!this.searchList.isEmpty()) {
			this.searchCurr = this.searchList.remove(0);

			int col;

			if (this.searchCurr.stepsToEnd > 255) {
				col = 255;
			} else {
				col = this.searchCurr.stepsToEnd;
			}

			// creates gradient
			this.searchCurr.color = new Color(255 - col, 0, col);

			// System.out.println(this.searchCurr.stepsToEnd);

			if (this.searchCurr.equals(this.searchTarget)) {
				if (this.searchingBFS) {
					this.generatingPath = true;
					this.searchingBFS = false;
					this.searchedBFS = true;
					this.searchPath.add(this.cells.get(0).get(0));
					this.bfsSteps++;
				} else if (this.searchingDFS) {
					this.generatingPath = true;
					this.searchingDFS = false;
					this.searchedDFS = true;
					this.searchPath.add(this.cells.get(0).get(0));
					this.dfsSteps++;
				}
			}

			else if (this.searchCurr.seen) {
				// do nothing, already seen this element
				// System.out.println("already seen this");
			}

			else {
				if (this.searchingBFS) {
					this.bfsSteps++;
				}

				if (this.searchingDFS) {
					this.dfsSteps++;
				}

				for (Edge e : this.searchCurr.edgeList) {

					if (this.searchingBFS && !e.to.seen) {
						this.searchList.add(e.to);
					}

					else if (this.searchingDFS && !e.to.seen) {
						this.searchList.addFirst(e.to);
					}
				}
				this.searchCurr.seen = true;
			}
		}
	}

	// EFFECT: resets maze
	public void resetMaze() {
		for (List<Cell> l : this.cells) {
			for (Cell c : l) {
				c.color = Color.GRAY;
				c.seen = false;
			}
		}
		this.cells.get(0).get(0).color = Color.green;
		this.cells.get(MazeWorld.MAZE_WIDTH - 1).get(MazeWorld.MAZE_HEIGHT - 1).color = Color.magenta;
		this.backtrackingPath = false;
	}

	// EFFECT: generates a new maze
	public void newMaze() {
		this.cells = null;
		this.rand = new Random();
		this.allEdges = null;
		this.master = new ArrayList<Edge>();
		this.representatives = new HashMap<Posn, Posn>();
		this.worklist = null;
		this.makeCells();
		this.initConnections();
		this.makeMazeOnTick();
		this.searchCurr = null;
		this.searchTarget = null;
		this.searchList = null;
		this.searchPath = null;
		this.generatingPath = false;
		this.constructingMaze = true;
		this.canSearch = true;
		this.bfsSteps = 0;
		this.dfsSteps = 0;
		this.searchedBFS = false;
		this.searchedDFS = false;
		this.backtrackingPath = false;
	}

	// EFFECT: Handles key behavior
	@Override
	public void onKeyEvent(String s) {
		if (s.equals("d") && !this.searchingBFS && !this.searchingDFS && this.canSearch) {
			this.searchCurr = this.cells.get(0).get(0);
			this.searchTarget = this.cells.get(MazeWorld.MAZE_WIDTH - 1).get(MazeWorld.MAZE_HEIGHT - 1);
			this.searchingDFS = true;
			this.searchList = new LinkedList<Cell>();
			this.currPath = new LinkedList<Cell>();
			this.searchPath = new LinkedList<Cell>();
			this.searchList.add(this.searchCurr);
			this.canSearch = false;
			this.dfsSteps = 0;
		}

		else if (s.equals("b") && !this.searchingDFS && !this.searchingBFS && this.canSearch) {
			this.searchingBFS = true;
			this.searchCurr = this.cells.get(0).get(0);
			this.searchTarget = this.cells.get(MazeWorld.MAZE_WIDTH - 1).get(MazeWorld.MAZE_HEIGHT - 1);
			this.searchList = new LinkedList<Cell>();
			this.currPath = new LinkedList<Cell>();
			this.searchPath = new LinkedList<Cell>();
			this.searchList.add(this.searchCurr);
			this.canSearch = false;
			this.bfsSteps = 0;
		}

		else if (s.equals("r")) {
			this.searchingBFS = false;
			this.searchingDFS = false;
			this.generatingPath = false;
			this.searchPath = null;
			this.resetMaze();
			this.canSearch = true;
		}

		else if (s.equals("n")) {
			this.searchingBFS = false;
			this.searchingDFS = false;
			this.newMaze();
			this.canSearch = true;
		}
		else if (s.equals("k")) {
			if (makeMazeOnTick) {
				makeMazeOnTick = false;
			}
			else {
				makeMazeOnTick = true;
			}
		}
	}

	// EFFECT: handles tick behavior
	public void onTick() {
		if (this.constructingMaze && this.makeMazeOnTick) {
			this.knockWalls();
		}

		else if (this.constructingMaze) {
			this.constructingMaze = false;
			this.makeMaze();
		}

		else if (this.searchingDFS) {
			this.search();
		}

		else if (this.searchingBFS) {
			this.search();
		}

		else if (this.generatingPath) {
			this.generatePath();
			this.generatingPath = false;
			this.backtrackingPath = true;
		} else if (this.backtrackingPath) {
			this.backtrackPath();
		}
	}
}

// to define some examples of Maze stuff
class ExamplesMaze {
	MazeWorld maze = new MazeWorld();

	// instantiates the maze
	void instantiate() {
		if (this.maze.cells == null) {
			this.maze.makeCells();
			this.maze.initConnections();
			this.maze.makeMaze();
		} else {
			this.maze.cells = null;
			this.maze.master = null;
			this.maze.allEdges = null;
			this.maze.makeCells();
			this.maze.initConnections();
			this.maze.makeMaze();
		}
	}

	HashMap<Posn, Posn> testMap = new HashMap<Posn, Posn>();
	Posn posn1 = new Posn(0, 0);
	Posn posn2 = new Posn(0, 2);
	Posn posn3 = new Posn(0, 4);
	Posn posn4 = new Posn(0, 6);
	Posn posn5 = new Posn(2, 0);
	Posn posn6 = new Posn(2, 2);
	Posn posn7 = new Posn(2, 4);
	Posn posn8 = new Posn(2, 6);
	Posn posn9 = new Posn(4, 0);
	Posn posn10 = new Posn(4, 2);
	Posn posn11 = new Posn(4, 4);
	Posn posn12 = new Posn(4, 6);
	Posn posn13 = new Posn(6, 0);
	Posn posn14 = new Posn(6, 2);
	Posn posn15 = new Posn(6, 4);
	Posn posn16 = new Posn(6, 6);

	Cell cell1 = new Cell(new Posn(0, 0));
	Cell cell2 = new Cell(new Posn(0, 2));
	Cell cell3 = new Cell(new Posn(0, 4));
	Cell cell4 = new Cell(new Posn(0, 6));
	Cell cell5 = new Cell(new Posn(2, 0));
	Cell cell6 = new Cell(new Posn(2, 2));
	Cell cell7 = new Cell(new Posn(2, 4));
	Cell cell8 = new Cell(new Posn(2, 6));
	Cell cell9 = new Cell(new Posn(4, 0));
	Cell cell10 = new Cell(new Posn(4, 2));
	Cell cell11 = new Cell(new Posn(4, 4));
	Cell cell12 = new Cell(new Posn(4, 6));
	Cell cell13 = new Cell(new Posn(6, 0));
	Cell cell14 = new Cell(new Posn(6, 2));
	Cell cell15 = new Cell(new Posn(6, 4));
	Cell cell16 = new Cell(new Posn(6, 6));

	Edge edge1 = new Edge(this.cell1, this.cell2, 40);
	Edge edge2 = new Edge(this.cell1, this.cell2, 23);
	Edge edge3 = new Edge(this.cell1, this.cell2, 44);
	Edge edge4 = new Edge(this.cell1, this.cell2, 90);
	Edge edge5 = new Edge(this.cell1, this.cell2, 81);
	Edge edge6 = new Edge(this.cell1, this.cell2, 21);
	Edge edge7 = new Edge(this.cell1, this.cell2, 1);
	Edge edge8 = new Edge(this.cell1, this.cell2, 1000);

	// commented out tests which break the game upon TA request

	// // tests draw() in Cell
	// void testDrawCell(Tester t) {
	// t.checkExpect(this.cell3.draw(), new RectangleImage(Cell.size, Cell.size,
	// OutlineMode.SOLID, Color.gray));
	// }
	//
	// // tests place() in Cell
	// void testPlaceCell(Tester t) {
	// WorldScene drawnBoard = this.maze.getEmptyScene();
	// WorldScene drawnBoard2 = this.maze.getEmptyScene();
	//
	// drawnBoard2.placeImageXY(this.cell3.draw(), this.cell3.location.x + Cell.size
	// / 2,
	// this.cell3.location.y + Cell.size / 2);
	//
	// drawnBoard2.placeImageXY(new RectangleImage(Cell.size / 10, Cell.size / 10,
	// OutlineMode.SOLID, Color.black), this.cell3.location.x - Cell.size / 20,
	// this.cell3.location.y - Cell.size / 20);
	//
	// t.checkExpect(drawnBoard, this.maze.getEmptyScene());
	//
	// this.cell3.place(drawnBoard);
	//
	// t.checkExpect(drawnBoard, drawnBoard2);
	//
	// }
	//
	// // tests equals() in Edge
	// void testEdgeEquals(Tester t) {
	// t.checkExpect(this.edge1.equals(this.edge1), true);
	// t.checkExpect(this.edge1.equals(this.edge2), false);
	// }
	//
	// // tests drawing vertical walls
	// void testEdgeDrawVert(Tester t) {
	// t.checkExpect(this.edge1.drawVerticalWall(),
	// new RectangleImage(Cell.size / 10, Cell.size,
	// OutlineMode.SOLID, Color.black));
	// }
	//
	// // tests drawing horizontal walls
	// void testEdgeDrawHoriz(Tester t) {
	// t.checkExpect(this.edge1.drawHorizontalWall(),
	// new RectangleImage(Cell.size, Cell.size / 10,
	// OutlineMode.SOLID, Color.black));
	// }
	//
	// // tests place() in Edge
	// void testEdgePlace(Tester t) {
	// WorldScene drawnBoard = this.maze.getEmptyScene();
	// WorldScene drawnBoard2 = this.maze.getEmptyScene();
	//
	// this.cell1.bottom = this.cell2;
	//
	// this.edge1.place(drawnBoard2);
	//
	// drawnBoard.placeImageXY(this.edge1.drawHorizontalWall(),
	// (int)this.edge1.from.location.x + Cell.size / 2,
	// (int)(this.edge1.from.location.y + Cell.size * 0.96));
	//
	// t.checkExpect(drawnBoard, drawnBoard2);
	// }
	//
	// // tests makeCells()
	// void testMakeCells(Tester t) {
	// this.maze.cells = null;
	// this.maze.allEdges = null;
	// this.maze.master = null;
	// this.maze.makeCells();
	//
	// List<List<Cell>> testCells = new ArrayList<List<Cell>>();
	//
	// for (int i = 0; i < MazeWorld.MAZE_WIDTH; i++) {
	// List<Cell> currList = new ArrayList<Cell>();
	// for (int j = 0; j < MazeWorld.MAZE_HEIGHT; j++) {
	// currList.add(new Cell(new Posn(i * Cell.size, j * Cell.size)));
	// }
	// testCells.add(currList);
	// }
	//
	// testCells.get(0).get(0).color = Color.magenta;
	// testCells.get(MazeWorld.MAZE_WIDTH - 1).get(MazeWorld.MAZE_HEIGHT - 1).color
	// = Color.green;
	//
	// t.checkExpect(this.maze.cells, testCells);
	//
	// this.maze.cells = null;
	// }
	//
	// // tests initConnections()
	// void testInitConnections(Tester t) {
	// Random theRandom = new Random(1);
	//
	// MazeWorld maze2 = new MazeWorld(theRandom);
	// maze2.makeCells();
	// maze2.initConnections();
	//
	// List<Edge> testEdges = new ArrayList<Edge>();
	//
	// for (int i = 0; i < MazeWorld.MAZE_WIDTH; i++) {
	// for (int j = 0; j < MazeWorld.MAZE_HEIGHT; j++) {
	// Cell curr = maze2.cells.get(i).get(j);
	//
	// if (i < MazeWorld.MAZE_WIDTH - 1) {
	// Cell right = maze2.cells.get(i + 1).get(j);
	//
	// Edge currToRight = new Edge(curr, right, theRandom.nextInt(100));
	//
	// testEdges.add(currToRight);
	// }
	//
	// if (j < MazeWorld.MAZE_HEIGHT - 1) {
	// Cell bottom = maze2.cells.get(i).get(j + 1);
	//
	// Edge currToBottom = new Edge(curr, bottom, theRandom.nextInt(100));
	//
	// testEdges.add(currToBottom);
	// }
	// }
	// }
	//
	// t.checkExpect(maze2.allEdges, maze2.allEdges);
	// }
	//
	// // Are these two lists of Edges the same?
	// boolean sameEdgeList(List<Edge> l1, List<Edge> l2) {
	// for (int i = 0; i < l1.size(); i++) {
	// if (l2.get(i) == null || l1.get(i) != l2.get(i)) {
	// return false;
	// }
	// }
	// return true;
	// }
	//
	// // tests EdgeComparator.compare() and sameEdgeList()
	// void testEdgeCompAndSameEdgeList(Tester t) {
	// List<Edge> testEdgeList = new ArrayList<Edge>();
	//
	// testEdgeList.add(this.edge1);
	// testEdgeList.add(this.edge2);
	// testEdgeList.add(this.edge3);
	// testEdgeList.add(this.edge4);
	// testEdgeList.add(this.edge5);
	// testEdgeList.add(this.edge6);
	// testEdgeList.add(this.edge7);
	// testEdgeList.add(this.edge8);
	//
	// t.checkExpect(this.sameEdgeList(testEdgeList, testEdgeList), true);
	//
	// List<Edge> sortedEdgeList = new ArrayList<Edge>();
	//
	// sortedEdgeList.add(this.edge7);
	// sortedEdgeList.add(this.edge6);
	// sortedEdgeList.add(this.edge2);
	// sortedEdgeList.add(this.edge1);
	// sortedEdgeList.add(this.edge3);
	// sortedEdgeList.add(this.edge5);
	// sortedEdgeList.add(this.edge4);
	// sortedEdgeList.add(this.edge8);
	//
	// t.checkExpect(this.sameEdgeList(testEdgeList, sortedEdgeList), false);
	//
	// testEdgeList.sort(new EdgeComparator());
	//
	//
	// t.checkExpect(this.sameEdgeList(testEdgeList, sortedEdgeList), true);
	// }

	List<Cell> cellList1 = new ArrayList<Cell>();
	List<Cell> cellList2 = new ArrayList<Cell>();
	List<Cell> cellList3 = new ArrayList<Cell>();
	List<Cell> cellList4 = new ArrayList<Cell>();

	List<List<Cell>> theCells = new ArrayList<List<Cell>>();

	// inits testMap another way for testing purposes
	void initTestMap() {
		this.testMap.put(this.posn1, this.posn4);
		this.testMap.put(this.posn2, this.posn2);
		this.testMap.put(this.posn3, this.posn3);
		this.testMap.put(this.posn4, this.posn5);
		this.testMap.put(this.posn6, this.posn6);
		this.testMap.put(this.posn7, this.posn8);
		this.testMap.put(this.posn5, this.posn8);
		this.testMap.put(this.posn8, this.posn8);
		this.testMap.put(this.posn9, this.posn9);
		this.testMap.put(this.posn4, this.posn7);
	}

	// // tests union()
	// void testUnion(Tester t) {
	// initTestMap();
	// t.checkExpect(this.maze.find(this.testMap, this.posn1), this.posn8);
	// this.testMap.put(this.posn8, this.posn9);
	// this.testMap.remove(this.posn8, this.posn8);
	// t.checkExpect(this.maze.find(this.testMap, this.posn1), this.posn9);
	// }

	// instantiates testMap for testing purposes
	void instantiateTestMap() {
		this.testMap.put(this.posn1, this.posn1);
		this.testMap.put(this.posn2, this.posn1);
		this.testMap.put(this.posn3, this.posn1);
		this.testMap.put(this.posn4, this.posn3);
		this.testMap.put(this.posn5, this.posn1);
		this.testMap.put(this.posn6, this.posn5);
		this.testMap.put(this.posn7, this.posn6);
		this.testMap.put(this.posn8, this.posn7);
		this.testMap.put(this.posn9, this.posn8);
		this.testMap.put(this.posn10, this.posn9);
		this.testMap.put(this.posn11, this.posn10);
		this.testMap.put(this.posn12, this.posn4);
		this.testMap.put(this.posn13, this.posn12);
		this.testMap.put(this.posn14, this.posn13);
		this.testMap.put(this.posn15, this.posn14);
		this.testMap.put(this.posn16, this.posn15);

		this.cellList1.add(this.cell1);
		this.cellList1.add(this.cell2);
		this.cellList1.add(this.cell3);
		this.cellList1.add(this.cell4);
		this.cellList2.add(this.cell5);
		this.cellList2.add(this.cell6);
		this.cellList2.add(this.cell7);
		this.cellList2.add(this.cell8);
		this.cellList3.add(this.cell9);
		this.cellList3.add(this.cell10);
		this.cellList3.add(this.cell11);
		this.cellList3.add(this.cell12);
		this.cellList4.add(this.cell13);
		this.cellList4.add(this.cell14);
		this.cellList4.add(this.cell15);
		this.cellList4.add(this.cell16);

		this.theCells.add(this.cellList1);
		this.theCells.add(this.cellList2);
		this.theCells.add(this.cellList3);
		this.theCells.add(this.cellList4);

		maze.cells = this.theCells;
	}

	// // tests find()
	// void testFind(Tester t) {
	// List<List<Cell>> oldCells = new ArrayList<List<Cell>>();
	// oldCells = this.maze.cells;
	// instantiateTestMap();
	// t.checkExpect(this.maze.find(this.testMap, this.posn1), this.posn1);
	// t.checkExpect(this.maze.find(this.testMap, this.posn16), this.posn1);
	// t.checkExpect(this.maze.find(this.testMap, this.posn4), this.posn1);
	// t.checkExpect(this.maze.find(this.testMap, this.posn6), this.posn1);
	// this.maze.cells = oldCells;
	// }
	//
	// // tests moreThanOneTree()
	// void testMoreThanOneTree(Tester t) {
	// instantiateTestMap();
	// t.checkExpect(this.maze.moreThanOneTree(this.testMap), false);
	// this.testMap.remove(this.posn16, this.posn15);
	// this.testMap.put(this.posn16, this.posn16);
	// t.checkExpect(this.maze.moreThanOneTree(this.testMap), true);
	// }
	//
	// // tests makeScene()
	// void testMakeScene(Tester t) {
	// instantiate();
	//
	// WorldScene drawnBoard = this.maze.getEmptyScene();
	//
	// for (List<Cell> l : this.maze.cells) {
	// for (Cell c : l) {
	// c.place(drawnBoard);
	// }
	// }
	//
	// for (Edge e : this.maze.allEdges) {
	// if (! this.maze.master.contains(e)) {
	// e.place(drawnBoard);
	// }
	// }
	//
	// WorldImage vert = new RectangleImage(Cell.size / 10,
	// Cell.size * MazeWorld.MAZE_HEIGHT, OutlineMode.SOLID, Color.black);
	// WorldImage horiz = new RectangleImage(Cell.size * MazeWorld.MAZE_WIDTH,
	// Cell.size / 10, OutlineMode.SOLID, Color.black);
	//
	// int h = (int)(Cell.size * MazeWorld.MAZE_HEIGHT);
	// int w = (int)(Cell.size * MazeWorld.MAZE_WIDTH);
	// drawnBoard.placeImageXY(vert, Cell.size / 20, h / 2);
	// drawnBoard.placeImageXY(vert, w - (Cell.size / 20), h / 2);
	// drawnBoard.placeImageXY(horiz, w / 2, Cell.size / 20);
	// drawnBoard.placeImageXY(horiz, w / 2, h - (Cell.size / 20));
	//
	// t.checkExpect(this.maze.makeScene(), drawnBoard);
	// }

	// tests hasPathTo() in Cell
	void testHasPathTo(Tester t) {
		t.checkExpect(this.cell1.hasPathTo(this.cell2), false);
		this.cell1.edgeList.add(this.edge1);
		t.checkExpect(this.cell1.hasPathTo(this.cell2), true);
	}

	// tests getInverse in Edge
	void testGetInverse(Tester t) {
		t.checkExpect(this.edge1.getInverse(), new Edge(this.cell2, this.cell1, 40));
	}

	// tests continuesGettingCloser in Edge
	void testContinuesGettingCloser(Tester t) {
		this.cell1.stepsToEnd = 3;
		this.cell2.stepsToEnd = 2;
		this.cell3.stepsToEnd = 1;
		t.checkExpect(this.edge1.continuesGettingCloser(), false);
		this.cell2.edgeList.add(new Edge(this.cell2, this.cell3, 12));
		t.checkExpect(this.edge1.continuesGettingCloser(), true);
	}

	// makes a Maze
	void testMaze(Tester t) {
		instantiate();
		this.maze.bigBang(MazeWorld.MAZE_WIDTH * Cell.size + 300, MazeWorld.MAZE_HEIGHT * Cell.size, 1 / 10000.0);
		t.checkExpect(this.maze.cells.isEmpty(), false);
	}

	public static void main(String[] args) {		
		MazeWorld maze = new MazeWorld();
		if (args.length != 0) {

		}
		if (maze.cells == null) {
			maze.makeCells();
			maze.initConnections();
			maze.makeMaze();
		} else {
			maze.cells = null;
			maze.master = null;
			maze.allEdges = null;
			maze.makeCells();
			maze.initConnections();
			maze.makeMaze();
		}

		maze.bigBang(MazeWorld.MAZE_WIDTH * Cell.size + 300, MazeWorld.MAZE_HEIGHT * Cell.size, 1 / 100000.0);
	}
}
