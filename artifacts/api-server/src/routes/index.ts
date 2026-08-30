import { Router, type IRouter } from "express";
import healthRouter from "./health";
import examtubeRouter from "./examtube";

const router: IRouter = Router();

router.use(healthRouter);
router.use(examtubeRouter);

export default router;
